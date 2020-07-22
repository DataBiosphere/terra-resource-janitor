package bio.terra.janitor.db;

import bio.terra.generated.model.CloudResourceUid;
import com.google.auto.value.AutoValue;
import java.time.Instant;

/**
 * A single tracked resource for persistence.
 *
 * <p>The Janitor service attempts to cleanup resources that it is tracking once they expire. This
 * class describes a single resource being tracked.
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
