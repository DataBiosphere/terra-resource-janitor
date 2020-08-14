package bio.terra.janitor.db;

import bio.terra.generated.model.CloudResourceUid;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.auto.value.AutoValue;
import java.time.Instant;

/**
 * A resource being tracked for cleanup. This class represents a record in the tracked_resources
 * table in the Janitor's database.
 */
@AutoValue
@JsonSerialize(as = TrackedResource.class)
@JsonDeserialize(builder = AutoValue_TrackedResource.Builder.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_ARRAY)
public abstract class TrackedResource {
  @JsonProperty("trackedResourceId")
  public abstract TrackedResourceId trackedResourceId();

  @JsonProperty("trackedResourceState")
  public abstract TrackedResourceState trackedResourceState();

  @JsonProperty("cloudResourceUid")
  @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_ARRAY)
  public abstract CloudResourceUid cloudResourceUid();

  @JsonProperty("creation")
  public abstract Instant creation();

  @JsonProperty("expiration")
  public abstract Instant expiration();

  public static Builder builder() {
    return new AutoValue_TrackedResource.Builder();
  }

  public abstract Builder toBuilder();

  @AutoValue.Builder
  @JsonPOJOBuilder(withPrefix = "")
  public abstract static class Builder {
    public abstract Builder trackedResourceId(TrackedResourceId id);

    public abstract Builder trackedResourceState(TrackedResourceState trackedResourceState);

    public abstract Builder cloudResourceUid(CloudResourceUid cloudResourceUid);

    public abstract Builder creation(Instant creationTime);

    public abstract Builder expiration(Instant expirationTime);

    public abstract TrackedResource build();
  }
}
