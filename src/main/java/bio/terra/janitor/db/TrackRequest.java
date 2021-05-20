package bio.terra.janitor.db;

import bio.terra.janitor.generated.model.CloudResourceUid;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.Map;

/** A request to track a resource. This is a precursor to having a {@link TrackedResource}. */
@AutoValue
public abstract class TrackRequest {
  /** The id of the cloud resource to track. */
  public abstract CloudResourceUid cloudResourceUid();

  /** When the resource was created. */
  public abstract Instant creation();

  /** When the resource expires and can be cleaned up. */
  public abstract Instant expiration();

  /** The labels to associate with the resource. */
  public abstract ImmutableMap<String, String> labels();

  /** Additional metadata about the resource. */
  public abstract ResourceMetadata metadata();

  public static Builder builder() {
    return new AutoValue_TrackRequest.Builder().labels(ImmutableMap.of());
  }

  /** A builder for {@link TrackRequest}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder cloudResourceUid(CloudResourceUid cloudResourceUid);

    public abstract Builder creation(Instant creation);

    public abstract Builder expiration(Instant expiration);

    public abstract Builder labels(Map<String, String> labels);

    public abstract Builder metadata(ResourceMetadata value);

    public abstract TrackRequest build();
  }
}
