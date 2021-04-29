package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.janitor.generated.model.GoogleBigQueryTableUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to cleanup Google BigQuery Table resource. */
public class GoogleBigQueryTableCleanupStep extends ResourceCleanupStep {
  private final Logger logger = LoggerFactory.getLogger(GoogleBigQueryTableCleanupStep.class);
  private final BigQueryCow bigQueryCow;

  public GoogleBigQueryTableCleanupStep(BigQueryCow bigQueryCow, JanitorDao janitorDao) {
    super(janitorDao);
    this.bigQueryCow = bigQueryCow;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid) {
    GoogleBigQueryTableUid tableUid = resourceUid.getGoogleBigQueryTableUid();
    try {
      bigQueryCow
          .tables()
          .delete(tableUid.getProjectId(), tableUid.getDatasetId(), tableUid.getTableId())
          .execute();
      return StepResult.getStepResultSuccess();
    } catch (GoogleJsonResponseException e) {
      // If the dataset has already been deleted, this step is complete.
      if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return StepResult.getStepResultSuccess();
      }
      logger.warn("Exception during BigQuery Table Cleanup", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException e) {
      logger.warn("Exception during BigQuery Table Cleanup", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }
}
