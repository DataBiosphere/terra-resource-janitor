package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzureManagedIdentity;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import com.azure.resourcemanager.msi.MsiManager;

/** Step to clean up an Azure managed identity. */
public class AzureManagedIdentityCleanupStep extends ResourceCleanupStep {
  private final CrlConfiguration crlConfiguration;

  public AzureManagedIdentityCleanupStep(CrlConfiguration crlConfiguration, JanitorDao janitorDao) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzureManagedIdentity managedIdentity = resourceUid.getAzureManagedIdentity();
    MsiManager msiManager = crlConfiguration.buildMsiManager(managedIdentity.getResourceGroup());

    return AzureUtils.ignoreNotFound(
        () ->
            msiManager
                .identities()
                .deleteByResourceGroup(
                    managedIdentity.getResourceGroup().getResourceGroupName(),
                    managedIdentity.getIdentityName()));
  }
}
