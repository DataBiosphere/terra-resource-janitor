package bio.terra.janitor.service.janitor;

import bio.terra.generated.model.*;
import bio.terra.janitor.common.NotFoundException;
import bio.terra.janitor.common.exception.InternalServerErrorException;
import bio.terra.janitor.db.*;
import bio.terra.janitor.service.iam.AuthenticatedUserRequest;
import bio.terra.janitor.service.iam.IamService;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JanitorService {
  private final Logger logger = LoggerFactory.getLogger(JanitorService.class);

  private final JanitorDao janitorDao;
  private final TransactionTemplate transactionTemplate;
  private final IamService iamService;

  @Autowired
  public JanitorService(
      JanitorDao janitorDao, TransactionTemplate transactionTemplate, IamService iamService) {
    this.janitorDao = janitorDao;
    this.transactionTemplate = transactionTemplate;
    this.iamService = iamService;
  }

  public CreatedResource createResource(
      CreateResourceRequestBody body, AuthenticatedUserRequest userReq) {
    iamService.requireAdminUser(userReq);
    return createResourceInternal(body);
  }

  /** Internal method to Create a new {@link TrackedResource} without user credentials. */
  public CreatedResource createResourceInternal(CreateResourceRequestBody body) {
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
  public Optional<TrackedResourceInfo> getResource(String id, AuthenticatedUserRequest userReq) {
    iamService.requireAdminUser(userReq);
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
    return resourceAndLabels.map(JanitorService::createInfo);
  }

  /** Retrieves the resources with the {@link CloudResourceUid}. */
  public TrackedResourceInfoList getResources(
      CloudResourceUid cloudResourceUid, AuthenticatedUserRequest userReq) {
    iamService.requireAdminUser(userReq);
    List<TrackedResourceAndLabels> resourcesWithLabels =
        janitorDao.retrieveResourcesWith(cloudResourceUid);
    TrackedResourceInfoList resourceList = new TrackedResourceInfoList();
    resourcesWithLabels.stream()
        .map(JanitorService::createInfo)
        .forEach(resourceList::addResourcesItem);
    return resourceList;
  }

  /**
   * Updates the resource state.
   *
   * <p>Currently it supports:
   *
   * <ul>
   *   <li>Abandon resource: Update resource from READY or CLEANING to ABANDONED
   *   <li>Bump resource: Update resource from ABANDONED to READY
   * </ul>
   */
  public void updateResource(
      CloudResourceUid cloudResourceUid, ResourceState state, AuthenticatedUserRequest userReq) {
    iamService.requireAdminUser(userReq);
    if (state == ResourceState.ABANDONED) {
      // Update state to ABANDONED for READY or CLEANING state resources.
      List<TrackedResource> resources =
          getResourceWithState(
              cloudResourceUid, TrackedResourceState.READY, TrackedResourceState.CLEANING);

      if (resources.size() > 1) {
        throw new InternalServerErrorException(
            "More than one READY or CLEANING state resources are found.");
      }
      janitorDao.updateResourceState(
          resources.get(0).trackedResourceId(), TrackedResourceState.ABANDONED);
    } else if (state == ResourceState.READY) {
      // Bump ABANDONED state resources for cleaning.
      List<TrackedResource> resources =
          getResourceWithState(cloudResourceUid, TrackedResourceState.ABANDONED);

      // It is possible that there might be multiple ABANDON resources, and we only need to update
      // the one with last
      // expiration time.
      TrackedResource latestResource =
          resources.stream().max(Comparator.comparing(TrackedResource::expiration)).get();
      janitorDao.updateResourceState(
          latestResource.trackedResourceId(), TrackedResourceState.READY);
    }
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
