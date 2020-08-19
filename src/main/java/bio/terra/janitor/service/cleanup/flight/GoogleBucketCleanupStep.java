package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.storage.BlobCow;
import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.cloud.storage.StorageException;
import com.google.common.base.Stopwatch;
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
      System.out.println("~~~~~~COMPLETE START");
      Stopwatch stopwatch = Stopwatch.createStarted();
      // Delete all Blobs before deleting bucket.
      String bucketName = resourceUid.getGoogleBucketUid().getBucketName();
      BucketCow bucketCow = storageCow.get(bucketName);
      bucketCow.list().iterateAll().forEach(BlobCow::delete);
      System.out.println("~~~~~~COMPLETE START111111");
      System.out.println(stopwatch.elapsed().abs());
      bucketCow.delete();
      System.out.println("~~~~~~COMPLETE START22222222");
      System.out.println(stopwatch.elapsed().abs());
      System.out.println("~~~~~~COMPLETE CLEANUP");
      System.out.println(stopwatch.elapsed().abs());
      return StepResult.getStepResultSuccess();
    } catch (Exception e) {
      logger.warn("Exception occurs during Bucket Cleanup", e);
      // Catch all exceptions from GOOGLE and consider this retryable error.
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }
}
