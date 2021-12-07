package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzureNetwork;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import com.azure.resourcemanager.compute.ComputeManager;

/** Step to clean up an Azure network. */
public class AzureNetworkCleanupStep extends ResourceCleanupStep {
  private final CrlConfiguration crlConfiguration;

  public AzureNetworkCleanupStep(CrlConfiguration crlConfiguration, JanitorDao janitorDao) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzureNetwork network = resourceUid.getAzureNetwork();
    ComputeManager computeManager =
        crlConfiguration.buildComputeManager(network.getResourceGroup());

    return AzureUtils.ignoreNotFound(
        () ->
            computeManager
                .networkManager()
                .networks()
                .deleteByResourceGroup(
                    network.getResourceGroup().getResourceGroupName(), network.getNetworkName()));
  }
}
