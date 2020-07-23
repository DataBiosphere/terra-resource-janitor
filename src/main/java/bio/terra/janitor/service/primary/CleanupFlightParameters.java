package bio.terra.janitor.service.primary;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.db.TrackedResource;
import bio.terra.janitor.db.TrackedResourceId;
import bio.terra.stairway.FlightMap;
import java.util.UUID;

/**
 * The parameters for a cleanup flight to be stored on {@link bio.terra.stairway.FlightInput}.
 *
 * <p>This class must maintain backwards compatibility since it is serialized in Stairway as JSON.
 */
// TODO maybe autovalue with method to serialize on FlightMap.
public class CleanupFlightParameters {
  private static final String FLIGHT_MAP_KEY = "CleanupFlightParameters";

  private CloudResourceUid cloudResourceUid;
  private String trackedResourceId;

  public CloudResourceUid getCloudResourceUid() {
    return cloudResourceUid;
  }

  public CleanupFlightParameters setCloudResourceUid(CloudResourceUid cloudResourceUid) {
    this.cloudResourceUid = cloudResourceUid;
    return this;
  }

  public String getTrackedResourceId() {
    return trackedResourceId;
  }

  public CleanupFlightParameters setTrackedResourceId(String trackedResourceId) {
    this.trackedResourceId = trackedResourceId;
    return this;
  }

  /** Convenience method to create a {@link TrackedResourceId} from the String. */
  public TrackedResourceId trackedResourceId() {
    return TrackedResourceId.create(UUID.fromString(trackedResourceId));
  }

  /**
   * Puts this into the FlightMap. Only one {@link CleanupFlightParameters} should be on any given
   * {@link FlightMap}. Use with {@link #get(FlightMap)}.
   */
  public void put(FlightMap flightMap) {
    flightMap.put(FLIGHT_MAP_KEY, this);
  }

  /**
   * Retrieves a {@link CleanupFlightParameters} from the {@link FlightMap}. Use with {@link
   * #put(FlightMap)}.
   */
  public static CleanupFlightParameters get(FlightMap flightMap) {
    return flightMap.get(FLIGHT_MAP_KEY, CleanupFlightParameters.class);
  }

  public static CleanupFlightParameters from(TrackedResource trackedResource) {
    return new CleanupFlightParameters()
        .setCloudResourceUid(trackedResource.cloudResourceUid())
        .setTrackedResourceId(trackedResource.trackedResourceId().toString());
  }
}
