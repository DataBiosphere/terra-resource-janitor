package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.janitor.generated.model.GoogleBigQueryTableUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.cloud.bigquery.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to cleanup Google BigQuery Table resource. */
public class GoogleBigQueryTableCleanupStep extends ResourceCleanupStep {
  private final Logger logger = LoggerFactory.getLogger(GoogleBigQueryTableCleanupStep.class);
  private final BigQueryCow bigQueryCow;

  public GoogleBigQueryTableCleanupStep(ClientConfig clientConfig, JanitorDao janitorDao) {
    super(clientConfig, janitorDao);
    this.bigQueryCow = new BigQueryCow(clientConfig, BigQueryOptions.getDefaultInstance());
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid) {
    GoogleBigQueryTableUid bigQueryTableUid = resourceUid.getGoogleBigQueryTableUid();
    TableId tableId =
        TableId.of(
            bigQueryTableUid.getProjectId(),
            bigQueryTableUid.getDatasetId(),
            bigQueryTableUid.getTableId());
    try {
      bigQueryCow.delete(tableId);
      return StepResult.getStepResultSuccess();
    } catch (BigQueryException e) {
      logger.warn("Google BigQueryException occurs during BigQuery Table Cleanup", e);
      // Catch all exceptions from GOOGLE and consider this retryable error.
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }
}
