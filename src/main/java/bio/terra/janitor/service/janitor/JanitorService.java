package bio.terra.janitor.service.janitor;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.CreateResourceRequestBody;
import bio.terra.generated.model.CreatedResource;
import bio.terra.janitor.db.JanitorDao;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    CloudResourceUid cloudResourceUid = body.getResourceUid();
    Instant creationTime = Instant.now();
    return new CreatedResource()
        .id(
            janitorDao
                .createResource(
                    cloudResourceUid,
                    body.getLabels(),
                    creationTime,
                    creationTime.plus(body.getTimeToLiveInMinutes(), ChronoUnit.MINUTES))
                .toString());
  }
}
