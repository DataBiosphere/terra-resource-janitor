package bio.terra.janitor.service.iam;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.janitor.app.configuration.IamConfiguration;
import bio.terra.janitor.common.exception.InvalidTestUserException;
import bio.terra.janitor.common.exception.SaCredentialsMissingException;
import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.cloud.iam.credentials.v1.IamCredentialsClient;
import com.google.cloud.iam.credentials.v1.ServiceAccountName;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
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

  // Duration that credentials impersonating test users should live, in seconds.
  private static final long IMPERSONATED_CREDENTIALS_TIMEOUT_SECONDS = 60 * 10; // 10m

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
      throw new InvalidTestUserException(
          "User "
              + userEmail
              + " is not a test user in the "
              + iamConfiguration.getTestUserDomain()
              + " domain.");
    }
  }

  /**
   * Generate an access token to impersonate the provided test user.
   *
   * <p>This relies on the Janitor SA's usage of Domain-Wide Delegation to impersonate users in the
   * test domain. Unfortunately, application-default credentials acquired via Workload Identity on
   * GKE do not appear to support DWD. Instead, this method builds a custom JWT with the "sub" claim
   * and then uses the ADC to sign the JWT via the IamCredentials API.
   */
  public String impersonateTestUser(String testUserEmail) {
    requireTestUser(testUserEmail);
    // Build a custom JWT. Setting the "sub" claim to the test user email means the returned JWT
    // will allow Janitor to impersonate the test user.
    Instant issuedAt = Instant.now();
    JsonObject jwtClaims = new JsonObject();

    jwtClaims.addProperty("iss", "janitor-default@terra-devel.iam.gserviceaccount.com");
    jwtClaims.addProperty("iat", issuedAt.getEpochSecond());
    jwtClaims.addProperty(
        "exp", (issuedAt.plusSeconds(IMPERSONATED_CREDENTIALS_TIMEOUT_SECONDS)).getEpochSecond());
    jwtClaims.addProperty("aud", GoogleOAuthConstants.TOKEN_SERVER_URL);
    jwtClaims.addProperty("sub", testUserEmail);
    jwtClaims.addProperty("scope", String.join(" ", USER_IMPERSONATION_SCOPES));
    // Per documentation, the `-` wildcard character is required; replacing it with a project ID is
    // invalid.
    ServiceAccountName janitorSaName =
        ServiceAccountName.of("-", "janitor-default@terra-devel.iam.gserviceaccount.com");
    // The client uses application-default credentials to make calls as the Janitor service account.
    try (IamCredentialsClient client = IamCredentialsClient.create()) {
      return client
          .signJwt(janitorSaName, USER_IMPERSONATION_SCOPES, jwtClaims.toString())
          .getSignedJwt();
    } catch (IOException e) {
      throw new SaCredentialsMissingException(
          "Unable to load impersonate user via Janitor SA credentials", e);
    }
  }
}
