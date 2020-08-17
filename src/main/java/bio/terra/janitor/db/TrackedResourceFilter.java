package bio.terra.janitor.db;

import bio.terra.generated.model.CloudResourceUid;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/** A value class for filtering which resources are retrieved. */
@AutoValue
public abstract class TrackedResourceFilter {
  /** If not empty, only resources with states within this set are allowed. */
  public abstract ImmutableSet<TrackedResourceState> allowedStates();

  /** If not empty, only resources with states *not* within this set are allowed. */
  public abstract ImmutableSet<TrackedResourceState> forbiddenStates();

  /** If present, only resources with a matching CloudResourceUid are allowed. */
  public abstract Optional<CloudResourceUid> cloudResourceUid();

  /** If present, only resources with an expiration date less than or equal to this are allowed. */
  public abstract Optional<Instant> expiredBy();

  /** Creates a new builder that allows all resources. */
  public static Builder builder() {
    return new AutoValue_TrackedResourceFilter.Builder()
            .allowedStates(ImmutableSet.of())
            .forbiddenStates(ImmutableSet.of());
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder allowedStates(Set<TrackedResourceState> allowedStates);

    public abstract Builder forbiddenStates(Set<TrackedResourceState> forbiddenStates);

    public abstract Builder cloudResourceUid(CloudResourceUid cloudResourceUid);

    public abstract Builder expiredBy(Instant expiredBy);

    public abstract TrackedResourceFilter build();
  }
}
