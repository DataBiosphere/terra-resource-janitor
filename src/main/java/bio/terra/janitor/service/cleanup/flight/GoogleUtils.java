package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import java.io.IOException;
import java.time.Duration;

/** Utilities for working with Google APIs. */
public class GoogleUtils {
  private GoogleUtils() {}

  /** Returns whether a project is deleted or in the process of being deleted. */
  public static boolean deleteInProgress(Project project) throws IOException {
    return project.getState().equals("DELETE_REQUESTED")
        || project.getState().equals("DELETE_IN_PROGRESS");
  }

  /**
   * Poll until the Google Service API operation has completed. Throws any error or timeouts as a
   * {@link RetryException}.
   */
  public static void pollUntilSuccess(
      OperationCow<?> operation, Duration pollingInterval, Duration timeout)
      throws RetryException, IOException, InterruptedException {
    operation = OperationUtils.pollUntilComplete(operation, pollingInterval, timeout);
    if (operation.getOperationAdapter().getError() != null) {
      throw new RetryException(
          String.format(
              "Error polling operation. name [%s] message [%s]",
              operation.getOperationAdapter().getName(),
              operation.getOperationAdapter().getError().getMessage()));
    }
  }
}
