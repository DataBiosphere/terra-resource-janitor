package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzureContainerInstance;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import com.azure.resourcemanager.containerinstance.ContainerInstanceManager;

public class AzureContainerInstanceCleanupStep extends ResourceCleanupStep {
  private final CrlConfiguration crlConfiguration;

  public AzureContainerInstanceCleanupStep(
      CrlConfiguration crlConfiguration, JanitorDao janitorDao) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzureContainerInstance containerInstance = resourceUid.getAzureContainerInstance();
    ContainerInstanceManager containerInstanceManager =
        crlConfiguration.buildContainerInstance(containerInstance.getResourceGroup());

    return AzureUtils.ignoreNotFound(
        () ->
            containerInstanceManager
                .containerGroups()
                .deleteByResourceGroup(
                    containerInstance.getResourceGroup().getResourceGroupName(),
                    containerInstance.getContainerGroupName()));
  }
}
