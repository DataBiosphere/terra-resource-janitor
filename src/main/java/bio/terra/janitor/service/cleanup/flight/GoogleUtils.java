package bio.terra.janitor.service.cleanup.flight;

import com.google.api.services.cloudresourcemanager.v3.model.Project;
import java.io.IOException;

/** Utilities for working with Google APIs. */
public class GoogleUtils {
  private GoogleUtils() {}

  /** Returns whether a project is deleted or in the process of being deleted. */
  public static boolean deleteInProgress(Project project) throws IOException {
    return project.getState().equals("DELETE_REQUESTED")
        || project.getState().equals("DELETE_IN_PROGRESS");
  }
}
