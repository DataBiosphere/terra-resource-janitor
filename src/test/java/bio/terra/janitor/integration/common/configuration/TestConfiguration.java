package bio.terra.janitor.integration.common.configuration;

import com.google.auth.oauth2.ServiceAccountCredentials;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configs in integration test. */
@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "test")
public class TestConfiguration {
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
    try {
      return ServiceAccountCredentials.fromStream(
          Thread.currentThread()
              .getContextClassLoader()
              .getResourceAsStream(clientServiceAccountPath));
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to load GoogleCredentials from " + clientServiceAccountPath, e);
    }
  }
}
