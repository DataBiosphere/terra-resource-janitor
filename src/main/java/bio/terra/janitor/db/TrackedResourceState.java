package bio.terra.janitor.db;

/**
 * The state of the {@link TrackedResource}.
 *
 * <p>This is persisted as a string in the database, so the names of the enum values should not be
 * changed.
 */
public enum TrackedResourceState {
  READY,
  CLEANING,
  DONE,
  ERROR,
  ABANDONED,
  DUPLICATED,
}
