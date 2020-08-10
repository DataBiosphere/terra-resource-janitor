package bio.terra.janitor.service.janitor;

import bio.terra.generated.model.*;
import bio.terra.janitor.db.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
    TrackedResource resource =
        TrackedResource.builder()
            .trackedResourceId(TrackedResourceId.create(UUID.randomUUID()))
            .trackedResourceState(TrackedResourceState.READY)
            .cloudResourceUid(body.getResourceUid())
            .creation(body.getCreation().toInstant())
            .expiration(body.getExpiration().toInstant())
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
    Optional<TrackedResourceAndLabels> resourceAndLabels =
        janitorDao.retrieveResourceAndLabels(trackedResourceId);
    return resourceAndLabels.map(JanitorService::createInfo);
  }

  /** Retrieves the resources with the {@link CloudResourceUid}. */
  public TrackedResourceInfoList getResources(CloudResourceUid cloudResourceUid) {
    List<TrackedResourceAndLabels> resourcesWithLabels =
        janitorDao.retrieveResourcesWith(cloudResourceUid);
    TrackedResourceInfoList resourceList = new TrackedResourceInfoList();
    resourcesWithLabels.stream()
        .map(resourceWithLabels -> createInfo(resourceWithLabels))
        .forEach(resourceList::addResourcesItem);
    return resourceList;
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
