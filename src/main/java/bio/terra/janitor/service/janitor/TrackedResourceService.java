package bio.terra.janitor.service.janitor;

import bio.terra.generated.model.*;
import bio.terra.janitor.common.NotFoundException;
import bio.terra.janitor.db.*;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Services handles Janitor's tracked resource operations. */
@Component
public class TrackedResourceService {
  private final Logger logger = LoggerFactory.getLogger(TrackedResourceService.class);

  private final JanitorDao janitorDao;
  private final TransactionTemplate transactionTemplate;

  @Autowired
  public TrackedResourceService(JanitorDao janitorDao, TransactionTemplate transactionTemplate) {
    this.janitorDao = janitorDao;
    this.transactionTemplate = transactionTemplate;
  }

  /** Internal method to Create a new {@link TrackedResource} without user credentials. */
  public CreatedResource createResource(CreateResourceRequestBody body) {
    TrackedResourceId id =
        transactionTemplate.execute(status -> createResourceAndUpdateDuplicates(body, status));
    return new CreatedResource().id(id.toString());
  }

  /**
   * Create a new {@link TrackedResource} and update other resources with the same {@link
   * CloudResourceUid} as duplicated as appropriate. Returns the created resources {@link
   * TrackedResourceId}. This should be done as a part of a single database transaction.
   */
  private TrackedResourceId createResourceAndUpdateDuplicates(
      CreateResourceRequestBody body, TransactionStatus unused) {
    TrackedResourceId id = TrackedResourceId.create(UUID.randomUUID());
    TrackedResource resource =
        TrackedResource.builder()
            .trackedResourceId(id)
            .trackedResourceState(TrackedResourceState.READY)
            .cloudResourceUid(body.getResourceUid())
            .creation(body.getCreation().toInstant())
            .expiration(body.getExpiration().toInstant())
            .build();
    List<TrackedResource> duplicateResources =
        janitorDao.retrieveResourcesMatching(
            TrackedResourceFilter.builder()
                .cloudResourceUid(resource.cloudResourceUid())
                .forbiddenStates(
                    ImmutableSet.of(TrackedResourceState.DONE, TrackedResourceState.DUPLICATED))
                .build());
    if (!duplicateResources.isEmpty()) {
      if (duplicateResources.size() > 1) {
        // If all resources are created through this function, they should be duplicated
        // as new resources come in so that there are not multiple resources that need to
        // be marked as duplicated at once.
        logger.error(
            "There was more than one not DONE or DUPLICATE resource with the same CloudResourceUid {}",
            resource.cloudResourceUid());
      }
      Instant lastExpiration =
          duplicateResources.stream()
              .map(TrackedResource::expiration)
              .max(Instant::compareTo)
              .get();
      if (resource.expiration().isAfter(lastExpiration)) {
        // The new resource expires after existing resources. The other resources are now
        // duplicates of the new resource.
        for (TrackedResource duplicateResource : duplicateResources) {
          janitorDao.updateResourceState(
              duplicateResource.trackedResourceId(), TrackedResourceState.DUPLICATED);
        }
      } else {
        // There is a duplicating resource with a later or equal expiration time. The new
        // resource is duplicated on arrival.
        resource =
            resource.toBuilder().trackedResourceState(TrackedResourceState.DUPLICATED).build();
      }
    }
    janitorDao.createResource(resource, body.getLabels());
    return id;
  }

  /** Retrieves the info about a tracked resource if their exists a resource for that id. */
  public Optional<TrackedResourceInfo> getResource(String id) {
    UUID uuid;
    try {
      uuid = UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      // id did not match expected UUID format.
      return Optional.empty();
    }
    TrackedResourceId trackedResourceId = TrackedResourceId.create(uuid);
    Optional<TrackedResourceAndLabels> resourceAndLabels =
        janitorDao.retrieveResourceAndLabels(trackedResourceId);
    return resourceAndLabels.map(TrackedResourceService::createInfo);
  }

  /** Retrieves the resources with the {@link CloudResourceUid}. */
  public TrackedResourceInfoList getResources(CloudResourceUid cloudResourceUid) {
    List<TrackedResourceAndLabels> resourcesWithLabels =
        janitorDao.retrieveResourcesWith(cloudResourceUid);
    TrackedResourceInfoList resourceList = new TrackedResourceInfoList();
    resourcesWithLabels.stream()
        .map(TrackedResourceService::createInfo)
        .forEach(resourceList::addResourcesItem);
    return resourceList;
  }

  /** Updates the READY resource state to ABANDONED. */
  public void abandonResource(CloudResourceUid cloudResourceUid) {
    List<TrackedResource> resources =
        getResourceWithState(
            cloudResourceUid, TrackedResourceState.READY, TrackedResourceState.CLEANING);

    if (resources.size() > 1) {
      logger.error(
          "More than one READY or CLEANING state resources are found for resource {}.",
          cloudResourceUid);
    }
    resources.forEach(
        resource ->
            janitorDao.updateResourceState(
                resource.trackedResourceId(), TrackedResourceState.ABANDONED));
  }

  /**
   * Updates the ABANDONED/ERROR resource state to READY.
   *
   * <p>It is possible that there might be multiple ABANDONED resources, and we only need to update
   * the one with last expiration time.
   */
  public void bumpResource(CloudResourceUid cloudResourceUid) {
    List<TrackedResource> resources =
        getResourceWithState(
            cloudResourceUid, TrackedResourceState.ABANDONED, TrackedResourceState.ERROR);

    TrackedResource latestResource =
        resources.stream().max(Comparator.comparing(TrackedResource::expiration)).get();
    janitorDao.updateResourceState(latestResource.trackedResourceId(), TrackedResourceState.READY);
  }

  /**
   * Gets list of {@link TrackedResource} with {@link TrackedResourceState}.
   *
   * <p>Throws {@link NotFoundException} if no resource found.
   */
  private List<TrackedResource> getResourceWithState(
      CloudResourceUid resourceUid, TrackedResourceState... states) {
    List<TrackedResource> resources =
        janitorDao.retrieveResourcesMatching(
            TrackedResourceFilter.builder()
                .cloudResourceUid(resourceUid)
                .allowedStates(Sets.newHashSet(states))
                .build());

    if (resources.size() == 0) {
      throw new NotFoundException(
          String.format("Resource: %s not found with state %s", resourceUid, states));
    }
    return resources;
  }

  private static TrackedResourceInfo createInfo(TrackedResourceAndLabels resourceAndLabels) {
    TrackedResource resource = resourceAndLabels.trackedResource();
    return new TrackedResourceInfo()
        .id(resource.trackedResourceId().toString())
        .resourceUid(resource.cloudResourceUid())
        .state(resource.trackedResourceState().toString())
        .creation(OffsetDateTime.ofInstant(resource.creation(), ZoneOffset.UTC))
        .expiration(OffsetDateTime.ofInstant(resource.expiration(), ZoneOffset.UTC))
        .labels(resourceAndLabels.labels());
  }
}
