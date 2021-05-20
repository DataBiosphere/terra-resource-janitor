package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.notebooks.v1.model.Operation;
import com.google.api.services.notebooks.v1.model.Status;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleAiNotebookInstanceCleanupStep extends ResourceCleanupStep {
  private final Logger logger = LoggerFactory.getLogger(GoogleAiNotebookInstanceCleanupStep.class);
  private final AIPlatformNotebooksCow notebooksCow;
  private final CloudResourceManagerCow resourceManagerCow;

  public GoogleAiNotebookInstanceCleanupStep(
      AIPlatformNotebooksCow notebooksCow,
      CloudResourceManagerCow resourceManagerCow,
      JanitorDao janitorDao) {
    super(janitorDao);
    this.notebooksCow = notebooksCow;
    this.resourceManagerCow = resourceManagerCow;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata) {
    InstanceName instanceName =
        InstanceName.builder()
            .projectId(resourceUid.getGoogleAiNotebookInstanceUid().getProjectId())
            .location(resourceUid.getGoogleAiNotebookInstanceUid().getLocation())
            .instanceId(resourceUid.getGoogleAiNotebookInstanceUid().getInstanceId())
            .build();

    try {
      GoogleUtils.ProjectStatus projectStatus =
          GoogleUtils.checkProjectStatus(
              instanceName.projectId(), /* parentResource= */ Optional.empty(), resourceManagerCow);
      switch (projectStatus) {
        case DELETE_IN_PROGRESS:
          logger.info("Project for instance {} already being deleted.", instanceName.formatName());
          return StepResult.getStepResultSuccess();
        case PROBABLY_DOES_NOT_EXIST:
          logger.info(
              "Project for instance {} probably does not exist.", instanceName.formatName());
          return StepResult.getStepResultSuccess();
        case ACTIVE:
          // Continue to try to delete the instance.
          break;
        case FORBIDDEN:
          logger.info(
              "Unable to retrieve project for instance {}. Naively continuing to attempt delete.",
              instanceName.formatName());
          break;
      }
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

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
