package bio.terra.janitor.db;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/** A {@link TrackedResource} and the labels associated with it. */
@AutoValue
public abstract class TrackedResourceAndLabels {
  public abstract TrackedResource trackedResource();

  public abstract ImmutableMap<String, String> labels();

  public static TrackedResourceAndLabels create(
      TrackedResource trackedResource, Map<String, String> labels) {
    return builder().trackedResource(trackedResource).labels(labels).build();
  }

  public static Builder builder() {
    return new AutoValue_TrackedResourceAndLabels.Builder();
  }

  /** A builder for {@link TrackedResourceAndLabels}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder trackedResource(TrackedResource trackedResource);

    public abstract Builder labels(ImmutableMap<String, String> labels);

    public Builder labels(Map<String, String> labels) {
      return labels(ImmutableMap.copyOf(labels));
    }

    public abstract ImmutableMap.Builder<String, String> labelsBuilder();

    public abstract TrackedResourceAndLabels build();
  }
}
