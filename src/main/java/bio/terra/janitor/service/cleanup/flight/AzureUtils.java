package bio.terra.janitor.service.cleanup.flight;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.azure.core.management.exception.ManagementException;
import org.apache.commons.lang3.StringUtils;

/** Utilities for working with Azure APIs. */
public class AzureUtils {

  /** Runs an Azure operation and ignores ResourceNotFound. */
  public static StepResult ignoreNotFound(AzureExecute execute) {
    try {
      execute.execute();
      return StepResult.getStepResultSuccess();
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have deleted this resource.
      if (StringUtils.equals(e.getValue().getCode(), "ResourceNotFound")) {
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }

  @FunctionalInterface
  interface AzureExecute {
    void execute() throws ManagementException;
  }
}
