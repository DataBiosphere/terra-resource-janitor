package bio.terra.janitor.db;

/**
 * The state of the {@link TrackedResource}.
 *
 * <p>This is persisted as a string in the database, so the names of the enum values should not be
 * changed.
 */
public enum TrackedResourceState {
  // The initial state where no cleaning has happened to a resource yet. May not have expired.
  READY,
  // The resource is in the process of being cleaned up.
  CLEANING,
  // The resource was cleaned up successfully. There is nothing else to be done.
  DONE,
  // The resource could not be cleaned up. Manual intervention is required.
  ERROR,
  // The resource was manually abandoned and should not be cleaned up any more.
  ABANDONED,
  // The resource was duplicated by another tracked resource with the same cloud unique id.
  // Nothing else needs to be done for this resource.
  DUPLICATED,
}
