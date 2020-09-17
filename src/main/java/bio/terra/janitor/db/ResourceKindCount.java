package bio.terra.janitor.db;

import com.google.auto.value.AutoValue;

/**
 * A count of the number of tracked resources with of a certain kind in the database.
 *
 * <p>This is useful for exporting metrics about the current profile of the database. A single
 * {@link ResourceKindCount} is a row in a 'SELECT count(*), k1, k2, ... GROUP BY' query.
 */
@AutoValue
public abstract class ResourceKindCount {

  /** The number of tracked resources in this state. */
  public abstract int count();

  /** The state of the tracked resources. */
  public abstract TrackedResourceState trackedResourceState();

  /** The type of the tracked resources. */
  public abstract ResourceType resourceType();

  /**
   * The "client" label value of the tracked resources. May be empty if there is no label for that
   * key.
   */
  public abstract String client();

  public static Builder builder() {
    return new AutoValue_ResourceKindCount.Builder();
  }

  /** builder for {@link ResourceKindCount}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder count(int count);

    public abstract Builder trackedResourceState(TrackedResourceState trackedResourceState);

    public abstract Builder resourceType(ResourceType value);

    public abstract Builder client(String client);

    public abstract ResourceKindCount build();
  }
}
