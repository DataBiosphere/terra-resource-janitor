package bio.terra.janitor.db;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/**
 * Additional data about a tracked resource.
 *
 * <p>This metadata may be specific to a single resource type and, unlike the {@link
 * bio.terra.janitor.generated.model.CloudResourceUid}, does not help to uniquely identify a
 * resource.
 */
@AutoValue
public abstract class ResourceMetadata {
  /**
   * The parent resource name of a Google Project resource, e.g. "folders/1234" or
   * "organizations/1234". This must only be set for Google Project resources, but may be absent.
   */
  public abstract Optional<String> googleProjectParent();

  /**
   * The email identifier of the user who owns a workspace. Janitor will impersonate this user when
   * deleting the workspace. This must only be set for workspace resources, and must be present for
   * all workspace resources.
   */
  public abstract Optional<String> workspaceOwner();

  public static Builder builder() {
    return new AutoValue_ResourceMetadata.Builder();
  }

  /** Returns an empty {@link ResourceMetadata}. */
  public static ResourceMetadata none() {
    return ResourceMetadata.builder().build();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder googleProjectParent(Optional<String> googleProjectParent);

    public abstract Builder googleProjectParent(String googleProjectParent);

    public abstract Builder workspaceOwner(Optional<String> workspaceOwner);

    public abstract Builder workspaceOwner(String workspaceOwner);

    public abstract ResourceMetadata build();
  }
}
