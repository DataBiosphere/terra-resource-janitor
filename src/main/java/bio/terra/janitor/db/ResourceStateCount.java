package bio.terra.janitor.db;

import com.google.auto.value.AutoValue;

/**
 * A count of the number of tracked resources with a certain state in the database.
 *
 * <p>This is useful for exporting metrics about the current profile of the database.
 */
@AutoValue
public abstract class ResourceStateCount {

  /** The number of tracked resources in this state. */
  public abstract int count();

  /** The state of the tracked resources. */
  public abstract TrackedResourceState trackedResourceState();

  /**
   * The "client" label value of the tracked resources. May be empty if there is no label for that
   * key.
   */
  public abstract String clientId();

  public static Builder builder() {
    return new AutoValue_ResourceStateCount.Builder();
  }

  /** builder for {@link ResourceStateCount}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder count(int count);

    public abstract Builder trackedResourceState(TrackedResourceState trackedResourceState);

    public abstract Builder clientId(String clientId);

    public abstract ResourceStateCount build();
  }
}
