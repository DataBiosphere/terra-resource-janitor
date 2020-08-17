package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.TrackedResource;
import bio.terra.janitor.db.TrackedResourceId;
import bio.terra.janitor.db.TrackedResourceState;
import bio.terra.janitor.service.cleanup.CleanupParams;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleBucketCleanupStep implements Step {
  private Logger logger = LoggerFactory.getLogger(GoogleBucketCleanupStep.class);

  private final StorageCow storageCow;
  private final JanitorDao janitorDao;

  public GoogleBucketCleanupStep(ClientConfig clientConfig, JanitorDao janitorDao) {
    this.janitorDao = janitorDao;
    // Janitor only uses CRL Cows to delete resources. Cleanup is not needed.
    this.storageCow = new StorageCow(clientConfig, StorageOptions.getDefaultInstance());
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    // TODO(yonghao): Set bucket lifetime to 0 and wait once Stairway supports waiting. This is more
    // robust for buckets
    //  with many or very large objects.
    CleanupParams cleanupParams = new CleanupParams(flightContext.getInputParameters());
    CloudResourceUid cloudResourceUid = cleanupParams.getResourceUid();
    TrackedResourceId trackedResourceId = cleanupParams.getTrackedResourceId();
    Optional<TrackedResource> trackedResource =
        janitorDao.retrieveTrackedResource(trackedResourceId);
    // TrackedResource doesn't exist, this should not happen, fail the flight.
    if (!trackedResource.isPresent()) {
      logger.warn("trackedResource {} doesn't exist", trackedResourceId.toString());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }
    TrackedResourceState state = trackedResource.get().trackedResourceState();
    // State is DONE or ERROR, this should not happen, fail the flight.
    if (state.equals(TrackedResourceState.DONE) || state.equals(TrackedResourceState.ERROR)) {
      logger.warn("Unexpected trackedResource state for {}.", trackedResourceId.toString());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }
    // State is DUPLICATED or ABANDONED, this should not happen, complete the flight.
    if (state.equals(TrackedResourceState.DUPLICATED)
        || state.equals(TrackedResourceState.ABANDONED)) {
      logger.warn("Unexpected trackedResource state for {}.", trackedResourceId.toString());
      return StepResult.getStepResultSuccess();
    }
    try {
      storageCow.delete(cloudResourceUid.getGoogleBucketUid().getBucketName());
      return StepResult.getStepResultSuccess();
    } catch (StorageException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      // Catch all exceptions from GOOGLE and consider this retryable error.
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
