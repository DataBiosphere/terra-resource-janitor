package bio.terra.janitor.service.workspace;

import bio.terra.janitor.app.configuration.WorkspaceManagerConfiguration;
import bio.terra.janitor.service.iam.IamService;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import com.google.auth.oauth2.AccessToken;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Service which handles requests to Workspace Manager */
@Component
public class WorkspaceManagerService {

  private WorkspaceManagerConfiguration workspaceManagerConfiguration;
  private IamService iamService;
  // Client objects are heavyweight, so we reuse a single client despite calling multiple WSM
  // instances
  private final ApiClient apiClient;

  @Autowired
  public WorkspaceManagerService(
      WorkspaceManagerConfiguration workspaceManagerConfiguration, IamService iamService) {
    this.workspaceManagerConfiguration = workspaceManagerConfiguration;
    this.iamService = iamService;
    this.apiClient = new ApiClient();
  }

  public void cleanupWorkspace(UUID workspaceId, String testUser, String instanceId)
      throws ApiException {
    AccessToken userAccessToken = iamService.impersonateTestUser(testUser);
    apiClient
        .setBasePath(workspaceManagerConfiguration.getInstances().get(instanceId))
        .setAccessToken(userAccessToken.getTokenValue());
    WorkspaceApi workspaceClient = new WorkspaceApi();
    workspaceClient.setApiClient(apiClient);
    workspaceClient.deleteWorkspace(workspaceId);
  }
}
