package bio.terra.janitor.service.cleanup;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.db.TrackedResourceId;
import bio.terra.stairway.FlightMap;

/** Helper class to manage input parameters in a {@link FlightMap}. */
public final class CleanupParams {
  public static final String TRACKED_RESOURCE_ID = "trackedResourceId";
  public static final String CLOUD_RESOURCE_UID = "cloudResourceUid";

  private final FlightMap flightMap;

  public CleanupParams(FlightMap flightMap) {
    this.flightMap = flightMap;
  }

  public CloudResourceUid getResourceUid() {
    return flightMap.get(CLOUD_RESOURCE_UID, CloudResourceUid.class);
  }

  public TrackedResourceId getTrackedResourceId() {
    return flightMap.get(TRACKED_RESOURCE_ID, TrackedResourceId.class);
  }
}
