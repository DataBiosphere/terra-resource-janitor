package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzureNetworkSecurityGroup;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import com.azure.resourcemanager.compute.ComputeManager;

/** Step to clean up an Azure network security group. */
public class AzureNetworkSecurityGroupCleanupStep extends ResourceCleanupStep {
  private final CrlConfiguration crlConfiguration;

  public AzureNetworkSecurityGroupCleanupStep(
      CrlConfiguration crlConfiguration, JanitorDao janitorDao) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzureNetworkSecurityGroup networkSg = resourceUid.getAzureNetworkSecurityGroup();
    ComputeManager computeManager =
        crlConfiguration.buildComputeManager(networkSg.getResourceGroup());

    return AzureUtils.ignoreNotFound(
        () ->
            computeManager
                .networkManager()
                .networkSecurityGroups()
                .deleteByResourceGroup(
                    networkSg.getResourceGroup().getResourceGroupName(),
                    networkSg.getNetworkSecurityGroupName()));
  }
}
