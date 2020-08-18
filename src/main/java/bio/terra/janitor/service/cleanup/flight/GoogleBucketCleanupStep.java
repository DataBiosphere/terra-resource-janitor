package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.cloud.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to cleanup Google Bucket resource. */
public class GoogleBucketCleanupStep extends ResourceCleanupStep {
  private Logger logger = LoggerFactory.getLogger(GoogleBucketCleanupStep.class);

  public GoogleBucketCleanupStep(ClientConfig clientConfig, JanitorDao janitorDao) {
    super(clientConfig, janitorDao);
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid) {
    // TODO(yonghao): Set bucket lifetime to 0 and wait once Stairway supports waiting. This is more
    // robust for buckets with many or very large objects.
    // TODO(PF-29): Iterate through and delete all objects within the bucket.
    try {
      storageCow.delete(resourceUid.getGoogleBucketUid().getBucketName());
      return StepResult.getStepResultSuccess();
    } catch (StorageException e) {
      // Catch all exceptions from GOOGLE and consider this retryable error.
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }
}
