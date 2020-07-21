package bio.terra.janitor.db;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.common.ResourceType;
import com.google.auto.value.AutoValue;
import java.time.Instant;

/** A single tracked resource for persistence. */
@AutoValue
public abstract class TrackedResource {
  public abstract TrackedResourceId id();

  public abstract ResourceType resourceType();

  public abstract TrackedResourceState trackedResourceState();

  public abstract CloudResourceUid cloudResourceUid();

  public abstract Instant creationTime();

  public abstract Instant expirationTime();

  public static Builder builder() {
    return new AutoValue_TrackedResource.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder id(TrackedResourceId id);

    public abstract Builder resourceType(ResourceType resourceType);

    public abstract Builder trackedResourceState(TrackedResourceState trackedResourceState);

    public abstract Builder cloudResourceUid(CloudResourceUid cloudResourceUid);

    public abstract Builder creationTime(Instant creationTime);

    public abstract Builder expirationTime(Instant expirationTime);

    public abstract TrackedResource build();
  }
}
