package bio.terra.janitor.db;

import java.util.UUID;

/** Wraps the tracked_resource_id in db tracked_resource table. */
public class TrackedResourceId {
  private final UUID id;

  public TrackedResourceId() {
    this.id = UUID.randomUUID();
  }

  public UUID getUUID() {
    return this.id;
  }

  public String toString() {
    return id.toString();
  }
}
