package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.common.exception.InvalidMessageException;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.janitor.service.workspace.WorkspaceManagerService;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.client.ApiException;
import org.apache.http.HttpStatus;

public class TerraWorkspaceCleanupStep extends ResourceCleanupStep {

  private final WorkspaceManagerService workspaceManagerService;

  public TerraWorkspaceCleanupStep(
      WorkspaceManagerService workspaceManagerService, JanitorDao janitorDao) {
    super(janitorDao);
    this.workspaceManagerService = workspaceManagerService;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    String workspaceOwnerEmail =
        metadata
            .workspaceOwner()
            .orElseThrow(
                () ->
                    new InvalidMessageException(
                        "Workspace resource must have a test user in metadata"));
    try {
      if (resourceUid == null || resourceUid.getTerraWorkspace() == null) {
        throw new InvalidMessageException(
            "Cannot clean up Terra Workspace, request did not specify" + "a Terra Workspace UID");
      }
      workspaceManagerService.deleteWorkspace(
          resourceUid.getTerraWorkspace().getWorkspaceId(),
          workspaceOwnerEmail,
          resourceUid.getTerraWorkspace().getWorkspaceManagerInstance());
    } catch (ApiException e) {
      // Ignore NOT_FOUND responses from WSM, this or another flight has already succesfully deleted
      // the workspace. All other errors should be retried
      if (e.getCode() != HttpStatus.SC_NOT_FOUND) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }
    }
    return StepResult.getStepResultSuccess();
  }
}
