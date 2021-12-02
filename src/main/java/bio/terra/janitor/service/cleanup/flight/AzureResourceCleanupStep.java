package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.db.ResourceTypeVisitor;
import bio.terra.janitor.generated.model.AzureResourceGroup;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.janitor.service.cleanup.AzureResourceNameAndGroupVisitor;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.resources.fluentcore.arm.collection.SupportsDeletingByResourceGroup;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step to clean an Azure resource.
 *
 * <p>This step can be commonly used for different Azure resource types, as long as a {@link
 * SupportsDeletingByResourceGroup} implementation is provided for the resource type.
 */
public class AzureResourceCleanupStep extends ResourceCleanupStep {
  private static final Logger logger = LoggerFactory.getLogger(AzureResourceCleanupStep.class);
  private final Function<AzureResourceGroup, SupportsDeletingByResourceGroup> getDeleteClient;

  public AzureResourceCleanupStep(
      JanitorDao janitorDao,
      Function<AzureResourceGroup, SupportsDeletingByResourceGroup> getDeleteClient) {
    super(janitorDao);
    this.getDeleteClient = getDeleteClient;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {

    var resourceNameAndGroup = new AzureResourceNameAndGroupVisitor().accept(resourceUid).get();
    var deleteClient = getDeleteClient.apply(resourceNameAndGroup.getResourceGroup());

    try {
      deleteClient.deleteByResourceGroup(
          resourceNameAndGroup.getResourceGroup().getResourceGroupName(),
          resourceNameAndGroup.getResourceName());
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have deleted this resource.
      if (StringUtils.equals(e.getValue().getCode(), "ResourceNotFound")) {
        logger.info(
            "Azure resource of type {} with name {} in managed resource group {} already deleted",
            new ResourceTypeVisitor().accept(resourceUid).toString(),
            resourceNameAndGroup.getResourceName(),
            resourceNameAndGroup.getResourceGroup());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
