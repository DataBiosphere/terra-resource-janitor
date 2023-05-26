package bio.terra.janitor.service.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.app.configuration.IamConfiguration;
import bio.terra.janitor.common.BaseUnitTest;
import bio.terra.janitor.common.exception.InvalidTestUserException;
import com.google.cloud.iam.credentials.v1.IamCredentialsClient;
import com.google.cloud.iam.credentials.v1.ServiceAccountName;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;

@AutoConfigureMockMvc
public class IamServiceTest extends BaseUnitTest {

  @Autowired IamService iamService;
  @Autowired IamConfiguration iamConfiguration;

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  CrlConfiguration crlConfiguration;

  HttpClient mockGcpTokenClient = Mockito.mock(HttpClient.class, Mockito.RETURNS_DEEP_STUBS);
  IamCredentialsClient mockCredentialsClient =
      Mockito.mock(IamCredentialsClient.class, Mockito.RETURNS_DEEP_STUBS);

  @BeforeEach
  void setup() throws IOException {
    Mockito.when(
            crlConfiguration.getApplicationDefaultCredentials().getAccessToken().getTokenValue())
        .thenReturn("fakeJanitorSaToken");

    // Return a JSON object with both 'access_token' and 'email' fields for all tokenClient calls.
    // This is a bit of a hack to avoid more complex mocking for each individual call.
    JsonObject fakeResponseJson = new JsonObject();
    fakeResponseJson.addProperty("access_token", "MY_FAKE_ACCESS_TOKEN");
    fakeResponseJson.addProperty("email", "FAKE_JANITOR_SA_EMAIL");
    String serializedResponse = fakeResponseJson.toString();
    HttpEntity fakeResponse = new StringEntity(serializedResponse, ContentType.APPLICATION_JSON);
    Mockito.when(mockGcpTokenClient.execute(any()).getEntity()).thenReturn(fakeResponse);
    iamService.setGcpTokenClientClient(mockGcpTokenClient);

    Mockito.when(
            mockCredentialsClient
                .signJwt(eq(ServiceAccountName.of("-", "FAKE_JANITOR_SA_EMAIL")), any(), any())
                .getSignedJwt())
        .thenReturn("FAKE_JWT");
    iamService.setIamCredentialsClient(mockCredentialsClient);
  }

  @Test
  public void canImpersonateTestUser() {
    String testUser = "fake@" + iamConfiguration.getTestUserDomain();
    String fakeAccessToken = iamService.impersonateTestUser(testUser);
    assertEquals("MY_FAKE_ACCESS_TOKEN", fakeAccessToken);
  }

  @Test
  public void cannotImpersonateRealUser() {
    String invalidEmail = "fake@invalid.domain.com";
    assertThrows(
        InvalidTestUserException.class, () -> iamService.impersonateTestUser(invalidEmail));
  }
}
