package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import java.io.IOException;
import java.time.Duration;
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
  protected StepResult cleanUp(CloudResourceUid resourceUid)
      throws InterruptedException, RetryException {
    String projectId = resourceUid.getGoogleProjectUid().getProjectId();
    try {
      // We cannot distinguish between not having access to a project and the project no longer
      // existing. Google returns 403 for both cases to prevent project id probing. For now, we
      // think that due to how long it takes to delete a project vs marking it as ready for
      // deletion, we assume a 403 is for the forbidden case and is an actual error of not being
      // able to clean up with a resource. Therefore, we don't do special handling of the 403 here.
      Project project = resourceManagerCow.projects().get(projectId).execute();
      if (GoogleUtils.deleteInProgress(project)) {
        // Skip is project is deleted or being deleted.
        logger.info("Project id: {} is deleted or being deleted", projectId);
        return StepResult.getStepResultSuccess();
      }
      GoogleUtils.pollUntilSuccess(
          resourceManagerCow
              .operations()
              .operationCow(resourceManagerCow.projects().delete(projectId).execute()),
          Duration.ofSeconds(5),
          Duration.ofMinutes(5));
      return StepResult.getStepResultSuccess();
    } catch (IOException e) {
      logger.warn("IOException occurs during Google project Cleanup", e);
      // Catch all exceptions from GOOGLE and consider this retryable error.
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }
}
