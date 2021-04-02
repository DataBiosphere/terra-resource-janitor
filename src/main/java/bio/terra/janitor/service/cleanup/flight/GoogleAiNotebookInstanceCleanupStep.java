package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.notebooks.v1.model.Operation;
import com.google.api.services.notebooks.v1.model.Status;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleAiNotebookInstanceCleanupStep extends ResourceCleanupStep {
  private final Logger logger = LoggerFactory.getLogger(GoogleAiNotebookInstanceCleanupStep.class);

  public GoogleAiNotebookInstanceCleanupStep(ClientConfig clientConfig, JanitorDao janitorDao) {
    super(clientConfig, janitorDao);
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid) {
    AIPlatformNotebooksCow notebooksCow;
    try {
      notebooksCow =
          AIPlatformNotebooksCow.create(clientConfig, GoogleCredentials.getApplicationDefault());
    } catch (GeneralSecurityException | IOException e) {
      logger.warn("Failed to get applicatoin default Google Credentials", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    InstanceName instanceName =
        InstanceName.builder()
            .projectId(resourceUid.getGoogleAiNotebookInstanceUid().getProjectId())
            .location(resourceUid.getGoogleAiNotebookInstanceUid().getLocation())
            .instanceId(resourceUid.getGoogleAiNotebookInstanceUid().getInstanceId())
            .build();

    try {
      OperationCow<Operation> deleteOperation =
          notebooksCow
              .operations()
              .operationCow(notebooksCow.instances().delete(instanceName).execute());
      deleteOperation =
          OperationUtils.pollUntilComplete(
              deleteOperation, Duration.ofSeconds(30), Duration.ofMinutes(8));
      Status deleteStatus = deleteOperation.getOperation().getError();
      if (deleteStatus != null) {
        logger.warn(String.format("Error deleting notebooks instance: %s", deleteStatus));
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
      }
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        // Instance already deleted.
        return StepResult.getStepResultSuccess();
      }
      logger.warn("Exception during AI Notebook instance cleanup", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException | InterruptedException e) {
      logger.warn("Exception during AI Notebook instance cleanup", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
