package bio.terra.janitor.service.janitor;

import bio.terra.generated.model.CreateResourceRequestBody;
import bio.terra.generated.model.CreatedResource;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.TrackedResource;
import bio.terra.janitor.db.TrackedResourceId;
import bio.terra.janitor.db.TrackedResourceState;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
}
