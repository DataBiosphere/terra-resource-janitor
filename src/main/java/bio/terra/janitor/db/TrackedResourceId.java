package bio.terra.janitor.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.auto.value.AutoValue;
import java.util.UUID;

/** Wraps the tracked_resource_id in db tracked_resource table. */
@AutoValue
@JsonSerialize(as = TrackedResourceId.class)
@JsonDeserialize(builder = AutoValue_TrackedResourceId.Builder.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_ARRAY)
public abstract class TrackedResourceId {
  @JsonProperty("uuid")
  public abstract UUID uuid();

  public static TrackedResourceId create(UUID id) {
    return new AutoValue_TrackedResourceId.Builder().setUuid(id).build();
  }

  @Override
  public String toString() {
    return uuid().toString();
  }

  /** Builder for {@link TrackedResourceId}. */
  @AutoValue.Builder
  @JsonPOJOBuilder(withPrefix = "")
  public abstract static class Builder {
    public abstract Builder setUuid(UUID value);

    public abstract TrackedResourceId build();
  }
}
