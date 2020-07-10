package bio.terra.janitor.service.janitor;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.CreateResourceRequestBody;
import bio.terra.generated.model.CreatedResource;
import bio.terra.janitor.common.CloudResourceType;
import bio.terra.janitor.db.JanitorDao;
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
    System.out.println("~~~~~~~~~HERE!!!22222222");

    return new CreatedResource()
        .id(
            janitorDao
                .createResource(
                    cloudResourceUid,
                    CloudResourceType.getCloudResourceType(cloudResourceUid),
                    body.getLabels(),
                    body.getCreation(),
                    body.getCreation().plusMinutes(body.getTimeToLiveInMinutes()))
                .toString());
  }
}
