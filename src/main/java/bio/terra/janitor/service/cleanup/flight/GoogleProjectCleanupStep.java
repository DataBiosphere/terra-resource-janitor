package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to cleanup GCP project resource. */
public class GoogleProjectCleanupStep extends ResourceCleanupStep {
  private final Logger logger = LoggerFactory.getLogger(GoogleProjectCleanupStep.class);

  public GoogleProjectCleanupStep(ClientConfig clientConfig, JanitorDao janitorDao) {
    super(clientConfig, janitorDao);
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid) {
    CloudResourceManagerCow resourceManagerCow;
    try {
      resourceManagerCow =
          CloudResourceManagerCow.create(clientConfig, GoogleCredentials.getApplicationDefault());
    } catch (GeneralSecurityException | IOException e) {
      logger.warn("Failed to get application default Google Credentials", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    String projectId = resourceUid.getGoogleProjectUid().getProjectId();
    try {
      Project project = resourceManagerCow.projects().get(projectId).execute();

      if (project == null
          || project.getLifecycleState().equals("DELETE_REQUESTED")
          || project.getLifecycleState().equals("DELETE_IN_PROGRESS")) {
        // Skip is project is deleted or being deleted.
        logger.info("Project id: {} is being deleted", projectId);
        return StepResult.getStepResultSuccess();
      }

      resourceManagerCow.projects().delete(projectId).execute();
      return StepResult.getStepResultSuccess();
    } catch (IOException e) {
      logger.warn("IOException occurs during Google project Cleanup", e);
      // Catch all exceptions from GOOGLE and consider this retryable error.
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }
}
