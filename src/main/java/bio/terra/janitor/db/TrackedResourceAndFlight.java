package bio.terra.janitor.db;

import com.google.auto.value.AutoValue;

/** A {@link TrackedResource} with the {@link CleanupFlight} associated with it. */
@AutoValue
public abstract class TrackedResourceAndFlight {
  public abstract TrackedResource trackedResource();

  public abstract CleanupFlight cleanupFlight();

  public static TrackedResourceAndFlight create(
      TrackedResource trackedResource, CleanupFlight cleanupFlight) {
    return new AutoValue_TrackedResourceAndFlight(trackedResource, cleanupFlight);
  }
}
