package bio.terra.janitor.service.janitor;

import bio.terra.janitor.common.exception.NotFoundException;
import bio.terra.janitor.db.*;
import bio.terra.janitor.generated.model.*;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.time.Instant;
import java.util.*;
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

  /** Create a new {@link TrackedResource} tracking a resource. */
  public TrackedResource createResource(TrackRequest trackRequest) {
    return transactionTemplate.execute(
        status -> createResourceAndUpdateDuplicates(trackRequest, status));
  }

  /**
   * Create a new {@link TrackedResource} and update other resources with the same {@link
   * CloudResourceUid} as duplicated as appropriate. Returns the created resources {@link
   * TrackedResourceId}. This should be done as a part of a single database transaction.
   *
   * <p>Several parts of the Janitor system assume that there is at most one non-DONE, non-DUPLICATE
   * TrackedResource per CloudResourceUid. Change with care.
   */
  private TrackedResource createResourceAndUpdateDuplicates(
      TrackRequest trackRequest, TransactionStatus unused) {
    TrackedResourceId id = TrackedResourceId.create(UUID.randomUUID());
    TrackedResource resource =
        TrackedResource.builder()
            .trackedResourceId(id)
            .trackedResourceState(TrackedResourceState.READY)
            .cloudResourceUid(trackRequest.cloudResourceUid())
            .creation(trackRequest.creation())
            .expiration(trackRequest.expiration())
            .metadata(trackRequest.metadata())
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
          logger.info(
              "Duplicated resource, trackedResourceId: {}", duplicateResource.trackedResourceId());
        }
      } else {
        // There is a duplicating resource with a later or equal expiration time. The new
        // resource is duplicated on arrival.
        resource =
            resource.toBuilder().trackedResourceState(TrackedResourceState.DUPLICATED).build();
      }
    }
    janitorDao.createResource(resource, trackRequest.labels());
    return resource;
  }

  /**
   * Updates the READY resource state to ABANDONED.
   *
   * <p>Throws {@link NotFoundException} if there were no resources to abandon.
   */
  public void abandonResource(CloudResourceUid cloudResourceUid) {
    List<TrackedResource> abandonedResources =
        transactionTemplate.execute(status -> abandonResourceTransaction(cloudResourceUid, status));
    // Log only after the transaction has completed successfully so that we don't log something that
    // got rolled back.
    abandonedResources.forEach(
        resource ->
            logger.info("Abandoned resource, trackedResourceId: {}", resource.trackedResourceId()));
  }

  private List<TrackedResource> abandonResourceTransaction(
      CloudResourceUid cloudResourceUid, TransactionStatus unused) {
    List<TrackedResource> resources =
        getResourceWithState(
            cloudResourceUid,
            TrackedResourceState.READY,
            TrackedResourceState.CLEANING,
            TrackedResourceState.ERROR);

    if (resources.size() > 1) {
      logger.error(
          "More than one READY, CLEANING, or ERROR state resources are found during abandon for"
              + " resource {}.",
          cloudResourceUid);
    }
    resources.forEach(
        resource ->
            janitorDao.updateResourceState(
                resource.trackedResourceId(), TrackedResourceState.ABANDONED));
    return resources;
  }

  /**
   * Updates the ABANDONED/ERROR resource state to READY.
   *
   * <p>Throws {@link NotFoundException} if there were no resources to update.
   *
   * <p>There should be at most one ABANDONED/ERROR resources - the others should have been
   * DUPLICATED.
   */
  public void bumpResource(CloudResourceUid cloudResourceUid) {
    TrackedResourceId bumpedId =
        transactionTemplate.execute(status -> bumpResourceTransaction(cloudResourceUid, status));
    // Log only after the transaction has completed successfully so that we don't log something that
    // got rolled back.
    logger.info("Bump resource, trackedResourceId: {} ", bumpedId);
  }

  private TrackedResourceId bumpResourceTransaction(
      CloudResourceUid cloudResourceUid, TransactionStatus unused) {
    List<TrackedResource> resources =
        getResourceWithState(
            cloudResourceUid, TrackedResourceState.ABANDONED, TrackedResourceState.ERROR);

    if (resources.size() > 1) {
      logger.error(
          "More than one ABANDONED or ERROR state resources are found during bump for"
              + " resource {}.",
          cloudResourceUid);
    }

    // Pick the latest expiration, though there should have only been one resource.
    TrackedResource toBump =
        resources.stream().max(Comparator.comparing(TrackedResource::expiration)).get();
    janitorDao.updateResourceState(toBump.trackedResourceId(), TrackedResourceState.READY);
    return toBump.trackedResourceId();
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
}
