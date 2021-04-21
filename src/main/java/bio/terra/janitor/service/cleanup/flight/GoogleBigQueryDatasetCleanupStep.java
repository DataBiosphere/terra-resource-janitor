package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.janitor.generated.model.GoogleBigQueryDatasetUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to cleanup Google BigQuery Dataset resource. */
public class GoogleBigQueryDatasetCleanupStep extends ResourceCleanupStep {
  private final Logger logger = LoggerFactory.getLogger(GoogleBigQueryDatasetCleanupStep.class);

  public GoogleBigQueryDatasetCleanupStep(ClientConfig clientConfig, JanitorDao janitorDao) {
    super(clientConfig, janitorDao);
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid) {
    GoogleBigQueryDatasetUid datasetUid = resourceUid.getGoogleBigQueryDatasetUid();
    try {
      BigQueryCow bigQueryCow =
          BigQueryCow.create(clientConfig, GoogleCredentials.getApplicationDefault());
      // Because deleteContents is true, this call will delete datasets even if they still have
      // tables present.
      bigQueryCow
          .datasets()
          .delete(datasetUid.getProjectId(), datasetUid.getDatasetId())
          .setDeleteContents(true)
          .execute();
      return StepResult.getStepResultSuccess();
    } catch (GoogleJsonResponseException e) {
      // If the dataset has already been deleted, this step is complete.
      if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return StepResult.getStepResultSuccess();
      }
      logger.warn("Exception during Dataset Cleanup", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException | GeneralSecurityException e) {
      logger.warn("Exception during Dataset Cleanup", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }
}
