package bio.terra.janitor.db;

import bio.terra.generated.model.CloudResourceUid;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
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

  /** If present, only return up to {@code limit} resources. */
  public abstract OptionalInt limit();

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

    public abstract Builder limit(int value);

    abstract TrackedResourceFilter autoBuild();

    public TrackedResourceFilter build() {
      TrackedResourceFilter filter = autoBuild();
      Preconditions.checkArgument(
          Sets.intersection(filter.allowedStates(), filter.forbiddenStates()).isEmpty(),
          String.format(
              "TrackedResourceFilter contains states that are allowed and forbidden simultaneously: %s.",
              filter));
      return filter;
    }
  }
}
