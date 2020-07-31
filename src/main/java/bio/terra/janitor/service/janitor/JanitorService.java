package bio.terra.janitor.service.janitor;

import bio.terra.generated.model.*;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.TrackedResource;
import bio.terra.janitor.db.TrackedResourceId;
import bio.terra.janitor.db.TrackedResourceState;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JanitorService {

  private final JanitorDao janitorDao;

  @Autowired
  public JanitorService(JanitorDao janitorDao) {
    this.janitorDao = janitorDao;
  }

  public CreatedResource createResource(CreateResourceRequestBody body) {
    Instant creationTime = Instant.now();
    TrackedResource resource =
        TrackedResource.builder()
            .trackedResourceId(TrackedResourceId.create(UUID.randomUUID()))
            .trackedResourceState(TrackedResourceState.READY)
            .cloudResourceUid(body.getResourceUid())
            .creation(creationTime)
            .expiration(creationTime.plus(body.getTimeToLiveInMinutes(), ChronoUnit.MINUTES))
            .build();
    // TODO(yonghao): Solution for handling duplicate CloudResourceUid.
    janitorDao.createResource(resource, body.getLabels());
    return new CreatedResource().id(resource.trackedResourceId().toString());
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
    Optional<TrackedResource> resource = janitorDao.retrieveTrackedResource(trackedResourceId);
    if (!resource.isPresent()) {
      return Optional.empty();
    }
    Map<String, String> labels = janitorDao.retrieveLabels(trackedResourceId);
    return Optional.of(createInfo(resource.get(), labels));
  }

  /** Retrieves the resources with the {@link CloudResourceUid}. */
  public TrackedResourceInfoList getResources(CloudResourceUid cloudResourceUid) {
    List<JanitorDao.TrackedResourceAndLabels> resourcesWithLabels =
        janitorDao.retrieveResourcesWith(cloudResourceUid);
    TrackedResourceInfoList resourceList = new TrackedResourceInfoList();
    resourcesWithLabels.stream()
        .map(
            resourceWithLabels ->
                createInfo(resourceWithLabels.trackedResource(), resourceWithLabels.labels()))
        .forEach(resourceList::addResourcesItem);
    return resourceList;
  }

  private static TrackedResourceInfo createInfo(
      TrackedResource resource, Map<String, String> labels) {
    return new TrackedResourceInfo()
        .id(resource.trackedResourceId().toString())
        .resourceUid(resource.cloudResourceUid())
        .state(resource.trackedResourceState().toString())
        .creation(OffsetDateTime.ofInstant(resource.creation(), ZoneOffset.UTC))
        .expiration(OffsetDateTime.ofInstant(resource.expiration(), ZoneOffset.UTC))
        .labels(labels);
  }
}
