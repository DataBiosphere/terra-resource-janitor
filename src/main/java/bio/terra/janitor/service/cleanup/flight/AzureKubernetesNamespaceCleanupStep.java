package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzureKubernetesNamespace;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;

/** Step to clean up an Azure Kubernetes namespace. */
public class AzureKubernetesNamespaceCleanupStep extends ResourceCleanupStep {
  private final CrlConfiguration crlConfiguration;

  public AzureKubernetesNamespaceCleanupStep(
      CrlConfiguration crlConfiguration, JanitorDao janitorDao) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzureKubernetesNamespace namespace = resourceUid.getAzureKubernetesNamespace();

    // TODO

    return StepResult.getStepResultSuccess();
  }
}
