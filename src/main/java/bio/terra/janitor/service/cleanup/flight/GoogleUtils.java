package bio.terra.janitor.service.cleanup.flight;

import com.google.api.services.cloudresourcemanager.model.Project;
import java.io.IOException;

/** Utilities for working with Google APIs. */
public class GoogleUtils {
  private GoogleUtils() {}

  /** Returns whether a project is deleted or in the process of being deleted. */
  public static boolean deleteInProgress(Project project) throws IOException {
    return project.getLifecycleState().equals("DELETE_REQUESTED")
        || project.getLifecycleState().equals("DELETE_IN_PROGRESS");
  }
}
