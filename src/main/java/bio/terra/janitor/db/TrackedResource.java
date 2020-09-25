package bio.terra.janitor.db;

import bio.terra.janitor.generated.model.CloudResourceUid;
import com.google.auto.value.AutoValue;
import java.time.Instant;

/**
 * A resource being tracked for cleanup. This class represents a record in the tracked_resources
 * table in the Janitor's database.
 */
@AutoValue
public abstract class TrackedResource {
  public abstract TrackedResourceId trackedResourceId();

  public abstract TrackedResourceState trackedResourceState();

  public abstract CloudResourceUid cloudResourceUid();

  public abstract Instant creation();

  public abstract Instant expiration();

  public static Builder builder() {
    return new AutoValue_TrackedResource.Builder();
  }

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder trackedResourceId(TrackedResourceId id);

    public abstract Builder trackedResourceState(TrackedResourceState trackedResourceState);

    public abstract Builder cloudResourceUid(CloudResourceUid cloudResourceUid);

    public abstract Builder creation(Instant creationTime);

    public abstract Builder expiration(Instant expirationTime);

    public abstract TrackedResource build();
  }
}
