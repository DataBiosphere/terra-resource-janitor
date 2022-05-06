package bio.terra.janitor.service.iam;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.janitor.app.configuration.IamConfiguration;
import bio.terra.janitor.common.exception.SaCredentialsMissingException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * IAM Service which handles authorization. Currently, Janitor uses a simple user email allow list
 * to check user permission.
 *
 * <p>TODO(PF-81): Switch to use SAM when GKE SAM is ready to use.
 */
@Component
public class IamService {

  private static final List<String> USER_IMPERSONATION_SCOPES =
      ImmutableList.of("openid", "email", "profile");

  private final IamConfiguration iamConfiguration;

  @Autowired
  public IamService(IamConfiguration iamConfiguration) {
    this.iamConfiguration = iamConfiguration;
  }

  /** Check if user is an administrator. Throws {@link UnauthorizedException} if not. */
  public void requireAdminUser(AuthenticatedUserRequest userReq) {
    if (!iamConfiguration.isConfigBasedAuthzEnabled()) {
      return;
    }
    boolean isAdmin = iamConfiguration.getAdminUsers().contains(userReq.getEmail());
    if (!isAdmin) {
      throw new UnauthorizedException(
          "User " + userReq.getEmail() + " is not Janitor's administrator.");
    }
  }

  /**
   * Validate a given user email belongs to a test user domain, throw {@link UnauthorizedException}
   * if not.
   */
  public void requireTestUser(String userEmail) {
    if (!userEmail.endsWith("@" + iamConfiguration.getTestUserDomain())) {
      throw new UnauthorizedException(
          "User "
              + userEmail
              + " is not a test user in the "
              + iamConfiguration.getTestUserDomain()
              + " domain.");
    }
  }

  /**
   * Generate an access token to impersonate the provided test user. This relies on the Janitor SA's
   * usage of Domain-Wide Delegation to impersonate users in the test domain.
   */
  public AccessToken impersonateTestUser(String testUserEmail) {
    requireTestUser(testUserEmail);
    try {
      GoogleCredentials janitorSaCredentials = GoogleCredentials.getApplicationDefault();
      GoogleCredentials userCredentials =
          janitorSaCredentials
              .createScoped(USER_IMPERSONATION_SCOPES)
              .createDelegated(testUserEmail);
      userCredentials.refreshIfExpired();
      return userCredentials.getAccessToken();
    } catch (IOException e) {
      throw new SaCredentialsMissingException("Unable to load Janitor SA credentials", e);
    }
  }
}
