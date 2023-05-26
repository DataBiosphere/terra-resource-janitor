package bio.terra.janitor.service.iam;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.app.configuration.IamConfiguration;
import bio.terra.janitor.common.exception.InvalidTestUserException;
import bio.terra.janitor.common.exception.SaCredentialsMissingException;
import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.iam.credentials.v1.IamCredentialsClient;
import com.google.cloud.iam.credentials.v1.ServiceAccountName;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
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

  @VisibleForTesting static final String TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo";

  private static final String JWT_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";

  // Duration that credentials impersonating test users should live, in seconds.
  private static final long IMPERSONATED_CREDENTIALS_TIMEOUT_SECONDS = 60 * 10; // 10m

  private final IamConfiguration iamConfiguration;
  private final CrlConfiguration crlConfiguration;
  private HttpClient gcpTokenClient;
  private IamCredentialsClient iamCredentialsClient;

  @Autowired
  public IamService(IamConfiguration iamConfiguration, CrlConfiguration crlConfiguration)
      throws IOException {
    this.iamConfiguration = iamConfiguration;
    this.crlConfiguration = crlConfiguration;
    this.gcpTokenClient = HttpClients.createDefault();
    this.iamCredentialsClient = IamCredentialsClient.create();
  }

  @VisibleForTesting
  void setGcpTokenClientClient(HttpClient client) {
    this.gcpTokenClient = client;
  }

  @VisibleForTesting
  void setIamCredentialsClient(IamCredentialsClient client) {
    this.iamCredentialsClient = client;
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
   * test domain. Application-default credentials (ADC) acquired via Workload Identity on GKE do not
   * support DWD. Instead, this method builds a custom JWT with the subject claim, uses the ADC to
   * sign the JWT via the IamCredentials API, and finally exchanges the signed JWT for an OAuth
   * access token to call WSM.
   *
   * @param testUserEmail Email of the test user to impersonate. This will be validated against the
   *     domain allowed in configuration properties
   * @return OAuth access token authorizing the caller to impersonate the provided test user.
   */
  public String impersonateTestUser(String testUserEmail) {
    requireTestUser(testUserEmail);
    // By default, the IamCredentialsClient uses application-default credentials to make calls as
    // the Janitor service account.
    try {
      String janitorSaEmail = getAdcEmail(gcpTokenClient);
      // Per documentation, the `-` wildcard character is required; replacing it with a project ID
      // is invalid.
      ServiceAccountName janitorSaName = ServiceAccountName.of("-", janitorSaEmail);
      String signedJwt =
          iamCredentialsClient
              .signJwt(
                  janitorSaName,
                  /*delegates=*/ Collections.emptyList(),
                  buildJwtRequestBody(testUserEmail, janitorSaEmail))
              .getSignedJwt();
      // Google's auth library does not support exchanging a signed JWT for an access token yet,
      // so this needs to be an explicit POST call to the Google token server.
      URI fullUri =
          new URIBuilder(GoogleOAuthConstants.TOKEN_SERVER_URL)
              .addParameter("grant_type", JWT_GRANT_TYPE)
              .addParameter("assertion", signedJwt)
              .build();
      HttpPost tokenRequest = new HttpPost(fullUri);
      HttpResponse response = gcpTokenClient.execute(tokenRequest);
      return JsonParser.parseString(EntityUtils.toString(response.getEntity()))
          .getAsJsonObject()
          .get("access_token")
          .getAsString();
    } catch (IOException e) {
      throw new SaCredentialsMissingException(
          "Unable to impersonate test user via Janitor SA credentials", e);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not validate Janitor SA credentials.");
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Could not parse Google OAuth token server URL");
    }
  }

  /**
   * Get the email of the application default credentials. Generally, this should be the Janitor
   * service account. This requires a call to GCP's tokeninfo endpoint, as client libraries only
   * support introspection for ID tokens.
   */
  private String getAdcEmail(HttpClient client)
      throws IOException, GeneralSecurityException, URISyntaxException {
    GoogleCredentials janitorADC =
        crlConfiguration.getApplicationDefaultCredentials().createScoped("email");
    janitorADC.refreshIfExpired();
    URI tokenInfoUri =
        new URIBuilder(TOKEN_INFO_URL)
            .addParameter("access_token", janitorADC.getAccessToken().getTokenValue())
            .build();
    HttpGet tokenInfoRequest = new HttpGet(tokenInfoUri);
    HttpResponse response = client.execute(tokenInfoRequest);
    return JsonParser.parseString(EntityUtils.toString(response.getEntity()))
        .getAsJsonObject()
        .get("email")
        .getAsString();
  }

  /**
   * Build a request to generate and sign a custom JWT. Setting the subject claim to the test user
   * email means the returned JWT will allow Janitor to impersonate the test user.
   *
   * @param testUserEmail Email of the test user to impersonate
   * @param janitorSaEmail Email of the Janitor service account. Required as a JWT claim
   * @return A serialized JSON blob containing all JWT claims required to impersonate a user via
   *     domain-wide-delegation.
   */
  private String buildJwtRequestBody(String testUserEmail, String janitorSaEmail) {
    Instant issuedAt = Instant.now();
    JsonObject jwtClaims = new JsonObject();

    jwtClaims.addProperty("iss", janitorSaEmail);
    jwtClaims.addProperty("iat", issuedAt.getEpochSecond());
    jwtClaims.addProperty(
        "exp", (issuedAt.plusSeconds(IMPERSONATED_CREDENTIALS_TIMEOUT_SECONDS)).getEpochSecond());
    jwtClaims.addProperty("aud", GoogleOAuthConstants.TOKEN_SERVER_URL);
    jwtClaims.addProperty("sub", testUserEmail);
    jwtClaims.addProperty("scope", String.join(" ", USER_IMPERSONATION_SCOPES));
    return jwtClaims.toString();
  }
}
