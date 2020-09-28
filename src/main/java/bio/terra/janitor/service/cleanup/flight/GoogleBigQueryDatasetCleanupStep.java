package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.DatasetId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to cleanup Google BigQuery Dataset resource. */
public class GoogleBigQueryDatasetCleanupStep extends ResourceCleanupStep {
  private final Logger logger = LoggerFactory.getLogger(GoogleBigQueryDatasetCleanupStep.class);
  private final BigQueryCow bigQueryCow;

  public GoogleBigQueryDatasetCleanupStep(ClientConfig clientConfig, JanitorDao janitorDao) {
    super(clientConfig, janitorDao);
    this.bigQueryCow = new BigQueryCow(clientConfig, BigQueryOptions.getDefaultInstance());
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid) {
    DatasetId datasetId =
        DatasetId.of(
            resourceUid.getGoogleBigQueryDatasetUid().getProjectId(),
            resourceUid.getGoogleBigQueryDatasetUid().getDatasetId());
    try {
      bigQueryCow.delete(datasetId, BigQuery.DatasetDeleteOption.deleteContents());
      return StepResult.getStepResultSuccess();
    } catch (BigQueryException e) {
      logger.warn("Google BigQueryException occurs during Dataset Cleanup", e);
      // Catch all exceptions from GOOGLE and consider this retryable error.
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }
}
