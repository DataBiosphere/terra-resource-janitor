package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import java.time.Duration;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to cleanup GCP project resource. */
public class GoogleProjectCleanupStep extends ResourceCleanupStep {
  private final Logger logger = LoggerFactory.getLogger(GoogleProjectCleanupStep.class);
  private final CloudResourceManagerCow resourceManagerCow;

  public GoogleProjectCleanupStep(
      CloudResourceManagerCow resourceManagerCow, JanitorDao janitorDao) {
    super(janitorDao);
    this.resourceManagerCow = resourceManagerCow;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    String projectId = resourceUid.getGoogleProjectUid().getProjectId();
    try {
      GoogleUtils.ProjectStatus projectStatus =
          GoogleUtils.checkProjectStatus(
              projectId, metadata.googleProjectParent(), resourceManagerCow);
      switch (projectStatus) {
        case FORBIDDEN:
          logger.info(
              "Forbidden from retrieving project id {}. It may or may not still exist.", projectId);
          // Optimistically fallthrough to trying to delete the project. Deletion will probably
          // fail.
        case ACTIVE:
          // If the project is still active, delete the project now.
          try {
            GoogleUtils.pollUntilSuccess(
                resourceManagerCow
                    .operations()
                    .operationCow(resourceManagerCow.projects().delete(projectId).execute()),
                Duration.ofSeconds(5),
                Duration.ofMinutes(5));
          } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
              // There's no use in retrying a forbidden error.
              return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
            }
            throw e;
          }
        case DELETING:
          // If the project is already being deleted, there's nothing else to do.
          logger.info("Project id: {} is already being deleted", projectId);
          return StepResult.getStepResultSuccess();
        case PROBABLY_DOES_NOT_EXIST:
          logger.info(
              "Project id {} probably does not exist. Counting this as successful deletion.",
              projectId);
          return StepResult.getStepResultSuccess();
      }
      return StepResult.getStepResultSuccess();
    } catch (IOException e) {
      logger.warn("IOException occurs during Google project Cleanup", e);
      // Catch all exceptions from GOOGLE and consider this retryable error.
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }
}
