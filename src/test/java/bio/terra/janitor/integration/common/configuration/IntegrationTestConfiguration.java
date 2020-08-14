package bio.terra.janitor.integration.common.configuration;

import static bio.terra.janitor.common.CredentialUtils.getGoogleCredentialsOrDie;

import com.google.auth.oauth2.ServiceAccountCredentials;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configs in integration test. */
@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "it")
public class IntegrationTestConfiguration {
  private String trackResourceTopicId;

  private String clientServiceAccountPath;

  public String getTrackResourceTopicId() {
    return trackResourceTopicId;
  }

  public void setTrackResourceTopicId(String trackResourceTopicId) {
    this.trackResourceTopicId = trackResourceTopicId;
  }

  public String getClientServiceAccountPath() {
    return clientServiceAccountPath;
  }

  public void setClientServiceAccountPath(String clientServiceAccountPath) {
    this.clientServiceAccountPath = clientServiceAccountPath;
  }

  /**
   * Janitor Client {@link ServiceAccountCredentials} which has permission to publish message to
   * Janitor.
   */
  public ServiceAccountCredentials getClientGoogleCredentialsOrDie() {
    return getGoogleCredentialsOrDie(clientServiceAccountPath);
  }
}
