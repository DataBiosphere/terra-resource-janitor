package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.StorageException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to cleanup Google Bucket resource. */
public class GoogleBucketCleanupStep extends ResourceCleanupStep {
  private final Logger logger = LoggerFactory.getLogger(GoogleBucketCleanupStep.class);
  private final StorageCow storageCow;

  public GoogleBucketCleanupStep(StorageCow storageCow, JanitorDao janitorDao) {
    super(janitorDao);
    this.storageCow = storageCow;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid) {
    // TODO(yonghao): Set bucket lifetime to 0 and wait once Stairway supports waiting. This is more
    // robust for buckets with many or very large objects.
    try {
      // Delete all Blobs before deleting bucket.
      String bucketName = resourceUid.getGoogleBucketUid().getBucketName();
      BucketCow bucketCow = storageCow.get(bucketName);
      if (bucketCow == null) {
        // Bucket doesn't exists.
        return StepResult.getStepResultSuccess();
      }
      List<BlobId> blobIds = new ArrayList<>();
      bucketCow
          .list()
          .iterateAll()
          .forEach(blobCow -> blobIds.add(blobCow.getBlobInfo().getBlobId()));
      blobIds.forEach(storageCow::delete);
      bucketCow.delete();
      return StepResult.getStepResultSuccess();
    } catch (StorageException e) {
      logger.warn("Google StorageException occurs during Bucket Cleanup", e);
      // Catch all exceptions from GOOGLE and consider this retryable error.
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }
}
