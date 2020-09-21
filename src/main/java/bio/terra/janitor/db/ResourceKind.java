package bio.terra.janitor.db;

import com.google.auto.value.AutoValue;

/** A value pair of a client label value and {@link ResourceType}. */
@AutoValue
public abstract class ResourceKind {

  /**
   * The "client" label value of the tracked resources. May be empty if there is no label for that
   * key.
   */
  public abstract String client();

  /** The type of the tracked resources. */
  public abstract ResourceType resourceType();

  public static ResourceKind create(String client, ResourceType resourceType) {
    return new AutoValue_ResourceKind(client, resourceType);
  }
}
