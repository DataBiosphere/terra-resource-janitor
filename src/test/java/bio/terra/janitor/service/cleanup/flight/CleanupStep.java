package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.StepResult;

public class CleanupStep extends ResourceCleanupStep {
  public CleanupStep(ClientConfig clientConfig, JanitorDao janitorDao) {
    super(clientConfig, janitorDao);
  }

  @Override
  StepResult cleanUp(CloudResourceUid resourceUid) {
    return null;
  }
}
