package bio.terra.janitor.service.cleanup.flight;

import static bio.terra.janitor.db.TrackedResourceState.CLEANING;

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
    if (state.equals(CLEANING)) {
      return cleanUp(cloudResourceUid);
    } else {
      throw new IllegalStateException(
          String.format(
              "Illegal trackedResource state for trackedResource %s, state is %s",
              trackedResourceId.toString(), state.toString()));
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }

  /** The actual resource cleanup logic. */
  protected abstract StepResult cleanUp(CloudResourceUid resourceUid);
}
