package bio.terra.janitor.db;

import com.google.auto.value.AutoValue;
import java.util.UUID;

/** Wraps the tracked_resource_id in db tracked_resource table. */
@AutoValue
public abstract class TrackedResourceId {
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
  public abstract static class Builder {
    public abstract Builder setUuid(UUID value);

    public abstract TrackedResourceId build();
  }
}
