package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzureNetwork;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to clean up an Azure network. */
public class AzureNetworkCleanupStep extends ResourceCleanupStep {
  private static final Logger logger = LoggerFactory.getLogger(AzureNetworkCleanupStep.class);
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

    try {
      computeManager
          .networkManager()
          .networks()
          .deleteByResourceGroup(
              network.getResourceGroup().getResourceGroupName(), network.getNetworkName());
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have deleted this resource.
      if (StringUtils.equals(e.getValue().getCode(), "ResourceNotFound")) {
        logger.info(
            "Azure network {} in managed resource group {} already deleted",
            network.getNetworkName(),
            network.getResourceGroup().getResourceGroupName());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
