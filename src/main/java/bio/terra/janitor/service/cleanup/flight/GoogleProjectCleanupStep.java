package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.api.services.common.Defaults;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.CloudResourceManagerScopes;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.auth.http.HttpCredentialsAdapter;
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
          new CloudResourceManagerCow(
              clientConfig,
              new CloudResourceManager.Builder(
                      Defaults.httpTransport(),
                      Defaults.jsonFactory(),
                      setHttpTimeout(
                          new HttpCredentialsAdapter(
                              GoogleCredentials.getApplicationDefault()
                                  .createScoped(CloudResourceManagerScopes.all()))))
                  .setApplicationName(clientConfig.getClientName()));
    } catch (GeneralSecurityException | IOException e) {
      logger.warn("Failed to get application default Google Credentials", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    String projectId = resourceUid.getGoogleProjectUid().getProjectId();
    try {
      // We cannot distinguish between not having access to a project and the project no longer
      // existing. Google returns 403 for both cases to prevent project id probing. For now, we
      // think that due to how long it takes to delete a project vs marking it as ready for
      // deletion, we assume a 403 is for the forbidden case and is an actual error of not being
      // able to clean up with a resource. Therefore, we don't do special handling of the 403 here.
      Project project = resourceManagerCow.projects().get(projectId).execute();

      if (project == null
          || project.getLifecycleState().equals("DELETE_REQUESTED")
          || project.getLifecycleState().equals("DELETE_IN_PROGRESS")) {
        // Skip is project is deleted or being deleted.
        logger.info("Project id: {} is deleted or being deleted", projectId);
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

  /** Sets longer timeout because ResourceManager operation may take longer than default timeout. */
  private static HttpRequestInitializer setHttpTimeout(
      final HttpRequestInitializer requestInitializer) {
    return httpRequest -> {
      requestInitializer.initialize(httpRequest);
      httpRequest.setConnectTimeout(3 * 60000); // 3 minutes connect timeout
      httpRequest.setReadTimeout(3 * 60000); // 3 minutes read timeout
    };
  }
}
