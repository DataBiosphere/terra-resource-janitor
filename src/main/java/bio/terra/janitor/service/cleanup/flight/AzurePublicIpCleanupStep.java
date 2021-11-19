package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzurePublicIp;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to clean up an Azure public IP. */
public class AzurePublicIpCleanupStep extends ResourceCleanupStep {
  private static final Logger logger = LoggerFactory.getLogger(AzurePublicIpCleanupStep.class);
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

    try {
      computeManager
          .networkManager()
          .publicIpAddresses()
          .deleteByResourceGroup(ip.getResourceGroup().getResourceGroupName(), ip.getIpName());
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have deleted this resource.
      if (StringUtils.equals(e.getValue().getCode(), "ResourceNotFound")) {
        logger.info(
            "Azure IP {} in managed resource group {} already deleted",
            ip.getIpName(),
            ip.getResourceGroup().getResourceGroupName());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
