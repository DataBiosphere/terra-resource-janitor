package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzurePublicIp;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import com.azure.resourcemanager.compute.ComputeManager;

/** Step to clean up an Azure public IP. */
public class AzurePublicIpCleanupStep extends ResourceCleanupStep {
  private final CrlConfiguration crlConfiguration;

  public AzurePublicIpCleanupStep(CrlConfiguration crlConfiguration, JanitorDao janitorDao) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzurePublicIp ip = resourceUid.getAzurePublicIp();
    ComputeManager computeManager = crlConfiguration.buildComputeManager(ip.getResourceGroup());

    return AzureUtils.ignoreNotFound(
        () -> {
          computeManager
              .networkManager()
              .publicIpAddresses()
              .deleteByResourceGroup(ip.getResourceGroup().getResourceGroupName(), ip.getIpName());
        });
  }
}
