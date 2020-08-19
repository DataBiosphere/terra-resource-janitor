package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.storage.BlobCow;
import bio.terra.cloudres.google.storage.BucketCow;
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
    try {
      // Delete all Blobs before deleting bucket.
      String bucketName = resourceUid.getGoogleBucketUid().getBucketName();
      BucketCow bucketCow = storageCow.get(bucketName);
      bucketCow.list().iterateAll().forEach(BlobCow::delete);
      bucketCow.delete();
      return StepResult.getStepResultSuccess();
    } catch (StorageException e) {
      logger.warn("Google Storage Exception happens during Bucket Cleanup", e);
      // Catch all exceptions from GOOGLE and consider this retryable error.
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }
}
