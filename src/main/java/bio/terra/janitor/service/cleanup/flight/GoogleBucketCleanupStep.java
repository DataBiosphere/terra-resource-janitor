package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.cloud.storage.BlobId;
import com.google.common.base.Stopwatch;
import java.util.ArrayList;
import java.util.List;
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
      System.out.println(GoogleCredential.getApplicationDefault().getServiceAccountId());
      Stopwatch stopwatch = Stopwatch.createStarted();
      // Delete all Blobs before deleting bucket.
      String bucketName = resourceUid.getGoogleBucketUid().getBucketName();
      BucketCow bucketCow = storageCow.get(bucketName);
      System.out.println("~~~~~~COMPLETE START00000");
      System.out.println(stopwatch.elapsed().abs().toMinutes());
      System.out.println(bucketCow.getBucketInfo().getName());
      List<BlobId> blobIds = new ArrayList<>();
      bucketCow.list();
      System.out.println("~~~~~~COMPLETE START111111222222");
      System.out.println(stopwatch.elapsed().abs().toMinutes());
      bucketCow
          .list()
          .iterateAll()
          .forEach(blobCow -> blobIds.add(blobCow.getBlobInfo().getBlobId()));
      System.out.println("~~~~~~COMPLETE START111111");
      System.out.println(stopwatch.elapsed().abs().toMinutes());
      blobIds.forEach(storageCow::delete);
      System.out.println("~~~~~~COMPLETE START22222222");
      System.out.println(stopwatch.elapsed().abs().toMinutes());
      bucketCow.delete();

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
