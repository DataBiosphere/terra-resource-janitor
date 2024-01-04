package bio.terra.janitor.service.workspace;

import bio.terra.janitor.app.configuration.WorkspaceManagerConfiguration;
import bio.terra.janitor.common.exception.InvalidResourceUidException;
import bio.terra.janitor.service.iam.IamService;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import jakarta.ws.rs.client.Client;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Service which handles requests to Workspace Manager */
@Component
public class WorkspaceManagerService {

  private final WorkspaceManagerConfiguration workspaceManagerConfiguration;
  private final IamService iamService;
  // Client objects are heavyweight, so we reuse a single client despite calling multiple WSM
  // instances.
  private final Client commonHttpClient;

  @Autowired
  public WorkspaceManagerService(
      WorkspaceManagerConfiguration workspaceManagerConfiguration, IamService iamService) {
    this.workspaceManagerConfiguration = workspaceManagerConfiguration;
    this.iamService = iamService;
    this.commonHttpClient = new ApiClient().getHttpClient();
  }

  private ApiClient getApiClient(String accessToken, String basePath) {
    ApiClient client = new ApiClient().setHttpClient(commonHttpClient).setBasePath(basePath);
    client.setAccessToken(accessToken);
    return client;
  }

  private WorkspaceApi getWorkspaceApi(String accessToken, String basePath) {
    ApiClient apiClient = getApiClient(accessToken, basePath);
    WorkspaceApi workspaceClient = new WorkspaceApi();
    workspaceClient.setApiClient(apiClient);
    return workspaceClient;
  }

  public void deleteWorkspace(UUID workspaceId, String testUser, String wsmUrl)
      throws ApiException {
    String userAccessToken = iamService.impersonateTestUser(testUser);
    if (!workspaceManagerConfiguration.getInstances().contains(wsmUrl)) {
      throw new InvalidResourceUidException("Invalid workspace instance url provided");
    }
    WorkspaceApi workspaceClient = getWorkspaceApi(userAccessToken, wsmUrl);
    workspaceClient.deleteWorkspace(workspaceId);
  }
}
