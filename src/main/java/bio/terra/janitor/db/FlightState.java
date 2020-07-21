package bio.terra.janitor.db;

/**
 * The state of a flight cleaning a {@link TrackedResource}.
 *
 * <p>This is persisted as a string in the database, so the names of the enum values should not be
 * changed.
 */
public enum FlightState {
  INITIATING,
  IN_FLIGHT,
  FINISHING,
  FATAL,
  FINISHED,
}
