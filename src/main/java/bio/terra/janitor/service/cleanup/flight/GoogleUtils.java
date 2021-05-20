package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.exception.RetryException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.api.services.cloudresourcemanager.v3.model.TestIamPermissionsRequest;
import com.google.api.services.cloudresourcemanager.v3.model.TestIamPermissionsResponse;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.apache.http.HttpStatus;

/** Utilities for working with Google APIs. */
public class GoogleUtils {
  private GoogleUtils() {}

  private static final String GET_PROJECT_PERMISSION = "resourcemanager.projects.get";

  /**
   * Try to get a project that may not exist.
   *
   * <p>We cannot distinguish between not having access to a project and the project no longer
   * existing. Google returns 403 for both cases to prevent project id probing.
   *
   * <p>For now, we think that due to how long it takes to delete a project vs marking it as ready
   * for deletion, if we know that we have permissions on the parent resource containing the
   * project, the project probably never existed. If we do not know the parent resource, or do not
   * have permissions on the project we assume a 403 is truly foribdden.
   */
  public static ProjectStatus tryGetProject(
      String projectId, Optional<String> parentResource, CloudResourceManagerCow resourceManager)
      throws IOException {
    try {
      Project project = resourceManager.projects().get(projectId).execute();
      // We were able to retrieve the project.
      return ProjectStatus.create(
          Optional.of(project),
          deleteInProgress(project)
              ? ProjectStatus.Status.DELETE_IN_PROGRESS
              : ProjectStatus.Status.ACTIVE);
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() != HttpStatus.SC_FORBIDDEN) {
        // Got an unexpected exception, throw to the caller.
        throw e;
      }
    }
    // If we reach here, we were forbidden from retrieving the project. We now try to determine if
    // we have enough permissions to assume the project does not exist.
    if (parentResource.isEmpty()
        || !hasProjectGetPermission(parentResource.get(), resourceManager)) {
      return ProjectStatus.create(Optional.empty(), ProjectStatus.Status.FORBIDDEN);
    } else {
      return ProjectStatus.create(Optional.empty(), ProjectStatus.Status.PROBABLY_DOES_NOT_EXIST);
    }
  }

  /**
   * Returns whether the credentials on the {@link CloudResourceManagerCow} have permissions to get
   * projects on the folder.
   */
  private static boolean hasProjectGetPermission(
      String parentResource, CloudResourceManagerCow resourceManager) throws IOException {
    if (!parentResource.contains("folders/")) {
      // We only support checking for permissions on folders right now.
      return false;
    }
    TestIamPermissionsResponse iamResponse =
        resourceManager
            .folders()
            .testIamPermissions(
                parentResource,
                new TestIamPermissionsRequest()
                    .setPermissions(ImmutableList.of(GET_PROJECT_PERMISSION)))
            .execute();
    return iamResponse.getPermissions().contains(GET_PROJECT_PERMISSION);
  }

  /** An optional Project and our best determination of the project's status. */
  @AutoValue
  public abstract static class ProjectStatus {
    /** The project, if it could be retrieved, i.e. Status is ACTIVE or DELETE_IN_PROGRESS */
    public abstract Optional<Project> project();

    enum Status {
      // The project is exists and is active.
      ACTIVE,
      // The project is in the process of being deleted. It is partially unavailable.
      DELETE_IN_PROGRESS,
      // We could not find the project, but we think we should have the permissions to do so. It's
      // likely that the project does not exist now, and maybe never existed.
      PROBABLY_DOES_NOT_EXIST,
      // We could not retrieve the project. It may or may not exist.
      FORBIDDEN,
    }

    public abstract Status status();

    public static ProjectStatus create(Optional<Project> project, Status status) {
      return new AutoValue_GoogleUtils_ProjectStatus(project, status);
    }
  }

  /** Returns whether a project is deleted or in the process of being deleted. */
  public static boolean deleteInProgress(Project project) throws IOException {
    return project.getState().equals("DELETE_REQUESTED")
        || project.getState().equals("DELETE_IN_PROGRESS");
  }

  /**
   * Poll until the Google Service API operation has completed. Throws any error or timeouts as a
   * {@link RetryException}.
   */
  public static void pollUntilSuccess(
      OperationCow<?> operation, Duration pollingInterval, Duration timeout)
      throws RetryException, IOException, InterruptedException {
    operation = OperationUtils.pollUntilComplete(operation, pollingInterval, timeout);
    if (operation.getOperationAdapter().getError() != null) {
      throw new RetryException(
          String.format(
              "Error polling operation. name [%s] message [%s]",
              operation.getOperationAdapter().getName(),
              operation.getOperationAdapter().getError().getMessage()));
    }
  }
}
