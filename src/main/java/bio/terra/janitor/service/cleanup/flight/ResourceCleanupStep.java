package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.db.*;
import bio.terra.janitor.service.cleanup.CleanupParams;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.cloud.storage.StorageOptions;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Abstract class for resource cleanup. */
public abstract class ResourceCleanupStep implements Step {
  private Logger logger = LoggerFactory.getLogger(ResourceCleanupStep.class);

  private final JanitorDao janitorDao;
  final StorageCow storageCow;

  public ResourceCleanupStep(ClientConfig clientConfig, JanitorDao janitorDao) {
    this.janitorDao = janitorDao;
    this.storageCow = new StorageCow(clientConfig, StorageOptions.getDefaultInstance());
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    // TODO(yonghao): Set bucket lifetime to 0 and wait once Stairway supports waiting. This is more
    // robust for buckets with many or very large objects.
    // TODO(PF-29): Iterate through and delete all objects within the bucket.
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
    switch (state) {
      case DONE:
      case ERROR:
      case READY:
        // State is DONE ERROR or READY, this should not happen, fail the flight.
        throw new IllegalStateException(
            String.format(
                "Illegal trackedResource state for trackedResource %s, state is %s",
                trackedResourceId.toString(), state.toString()));
      case DUPLICATED:
      case ABANDONED:
        // State is DUPLICATED or ABANDONED, skip the cleanup.
        logger.warn(
            "Skip resource {} cleanup because the state is {} due to a race.",
            trackedResourceId.toString(),
            state.toString());
        return StepResult.getStepResultSuccess();
      case CLEANING:
        // TODO(yonghao): Update cleanup log
        return cleanUp(cloudResourceUid);
      default:
        throw new UnsupportedOperationException(
            String.format(
                "Unsupported trackedResource state for trackedResource %s, state is %s",
                trackedResourceId.toString(), state.toString()));
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }

  /** The actual resource cleanup logic. */
  abstract StepResult cleanUp(CloudResourceUid resourceUid);
}
