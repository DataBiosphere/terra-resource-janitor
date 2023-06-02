package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzureStorageContainer;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import com.azure.resourcemanager.storage.StorageManager;


public class AzureStorageContainerCleanupStep extends ResourceCleanupStep {

  private final CrlConfiguration crlConfiguration;

  public AzureStorageContainerCleanupStep(CrlConfiguration crlConfiguration,
      JanitorDao janitorDao) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzureStorageContainer storageContainer = resourceUid.getAzureStorageContainer();
    StorageManager storageManager = crlConfiguration.buildStorageManager(
        storageContainer.getResourceGroup());

    return AzureUtils.ignoreNotFound(
        () ->
            storageManager
                .blobContainers()
                .delete(
                    storageContainer.getResourceGroup().getResourceGroupName(),
                    storageContainer.getStorageAccountName(),
                    storageContainer.getStorageContainerName()));
  }
}
