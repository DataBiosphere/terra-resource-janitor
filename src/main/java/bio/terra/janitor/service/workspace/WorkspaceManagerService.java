package bio.terra.janitor.service.workspace;

import bio.terra.janitor.app.configuration.WorkspaceManagerConfiguration;
import bio.terra.janitor.common.exception.InvalidResourceUidException;
import bio.terra.janitor.service.iam.IamService;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import com.google.auth.oauth2.AccessToken;
import java.util.Optional;
import java.util.UUID;
import javax.ws.rs.client.Client;
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

  public void deleteWorkspace(UUID workspaceId, String testUser, String instanceId)
      throws ApiException {
    AccessToken userAccessToken = iamService.impersonateTestUser(testUser);
    String wsmUrl =
        Optional.ofNullable(workspaceManagerConfiguration.getInstances().get(instanceId))
            .orElseThrow(
                () -> new InvalidResourceUidException("Invalid workspace instance identifier"));
    WorkspaceApi workspaceClient = getWorkspaceApi(userAccessToken.getTokenValue(), wsmUrl);
    workspaceClient.deleteWorkspace(workspaceId);
  }
}
