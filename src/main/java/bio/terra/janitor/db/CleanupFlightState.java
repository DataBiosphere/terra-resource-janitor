package bio.terra.janitor.db;

/**
 * The state of a flight cleaning a {@link TrackedResource}.
 *
 * <p>This is persisted as a string in the database, so the names of the enum values should not be
 * changed.
 */
public enum CleanupFlightState {
  // The flight is being handed off to Stairway.
  INITIATING,
  // The flight is being executed by Stairway.
  IN_FLIGHT,
  // The flight is ending its execution in Stairway. Stairway might not think the flight is done
  // yet. This is used to hand the flight back to the scheduler from Stairway.
  FINISHING,
  // The flight finished as an error or completion in Stairway normally.
  FINISHED,
  // The flight ended in the fatal, non-recoverable state in Stairway. It did not terminate
  // normally.
  FATAL,
}
