package bio.terra.janitor.db;

import com.google.auto.value.AutoValue;

/** A record of the flight cleaning a {@link TrackedResource}. */
@AutoValue
public abstract class CleanupFlight {
  public abstract String flightId();

  public abstract CleanupFlightState state();

  public static CleanupFlight create(String flightId, CleanupFlightState state) {
    return new AutoValue_CleanupFlight(flightId, state);
  }
}
