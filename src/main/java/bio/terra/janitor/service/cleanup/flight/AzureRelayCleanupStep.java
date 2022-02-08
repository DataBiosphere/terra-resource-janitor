 package bio.terra.janitor.service.cleanup.flight;

 import bio.terra.janitor.app.configuration.CrlConfiguration;
 import bio.terra.janitor.db.JanitorDao;
 import bio.terra.janitor.db.ResourceMetadata;
 import bio.terra.janitor.generated.model.AzureDisk;
 import bio.terra.janitor.generated.model.CloudResourceUid;
 import bio.terra.stairway.StepResult;
 import bio.terra.stairway.exception.RetryException;
 import com.azure.resourcemanager.compute.ComputeManager;

/** Step to clean up an Azure Relay. */
 public class AzureRelayCleanupStep extends ResourceCleanupStep {
  private final CrlConfiguration crlConfiguration;

  public AzureRelayCleanupStep(CrlConfiguration crlConfiguration, JanitorDao janitorDao) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzureDisk disk = resourceUid.getAzureDisk();
    ComputeManager computeManager = crlConfiguration.buildComputeManager(disk.getResourceGroup());

    return AzureUtils.ignoreNotFound(
        () ->
            computeManager
                .disks()
                .deleteByResourceGroup(
                    disk.getResourceGroup().getResourceGroupName(), disk.getDiskName()));
  }
 }
