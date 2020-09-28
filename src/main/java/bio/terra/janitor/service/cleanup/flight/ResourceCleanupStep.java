package bio.terra.janitor.service.cleanup.flight;

import static bio.terra.janitor.service.cleanup.FlightMapKeys.CLOUD_RESOURCE_UID;
import static bio.terra.janitor.service.cleanup.FlightMapKeys.TRACKED_RESOURCE_ID;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.janitor.db.*;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.*;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Abstract class for resource cleanup. */
public abstract class ResourceCleanupStep implements Step {
  private Logger logger = LoggerFactory.getLogger(ResourceCleanupStep.class);

  private final JanitorDao janitorDao;
  final ClientConfig clientConfig;

  public ResourceCleanupStep(ClientConfig clientConfig, JanitorDao janitorDao) {
    this.janitorDao = janitorDao;
    this.clientConfig = clientConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    FlightMap flightMap = flightContext.getInputParameters();
    CloudResourceUid cloudResourceUid = flightMap.get(CLOUD_RESOURCE_UID, CloudResourceUid.class);
    TrackedResourceId trackedResourceId =
        flightMap.get(TRACKED_RESOURCE_ID, TrackedResourceId.class);
    Optional<TrackedResource> trackedResource =
        janitorDao.retrieveTrackedResource(trackedResourceId);
    if (!trackedResource.isPresent()) {
      // TrackedResource doesn't exist, this should not happen, throw an exception.
      throw new IllegalStateException(
          String.format("TrackedResource %s, not found", trackedResourceId.toString()));
    }
    TrackedResourceState state = trackedResource.get().trackedResourceState();
    switch (state) {
      case DONE:
      case ERROR:
      case READY:
        // State is DONE ERROR or READY, this should not happen, throw an exception.
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
  protected abstract StepResult cleanUp(CloudResourceUid resourceUid);
}
