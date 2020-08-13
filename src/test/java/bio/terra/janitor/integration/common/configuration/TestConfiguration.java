package bio.terra.janitor.common.configuration;

import static bio.terra.janitor.common.CredentialUtils.getGoogleCredentialsOrDie;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.common.cleanup.CleanupConfig;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configs in integration test. */
@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "test")
public class TestConfiguration {
  /** How long to keep the resource before the 'prod' Janitor do the cleanup. */
  public static Duration RESOURCE_TIME_TO_LIVE = Duration.ofMinutes(30);

  private String resourceProjectId;
  private String trackResourceTopicId;
  private String clientServiceAccountPath;
  private String crlClientCredentialFilePath;
  private String janitorPubsubProjectId;
  private String janitorTopicId;

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

  public void setCrlClientCredentialFilePath(String crlClientCredentialFilePath) {
    this.crlClientCredentialFilePath = crlClientCredentialFilePath;
  }

  public void setJanitorPubsubProjectId(String janitorPubsubProjectId) {
    this.janitorPubsubProjectId = janitorPubsubProjectId;
  }

  public void setJanitorTopicId(String janitorTopicId) {
    this.janitorTopicId = janitorTopicId;
  }

  public String getResourceProjectId() {
    return resourceProjectId;
  }

  public void setResourceProjectId(String resourceProjectId) {
    this.resourceProjectId = resourceProjectId;
  }

  /**
   * Janitor Client {@link ServiceAccountCredentials} which has permission to publish message to
   * Janitor.
   */
  public ServiceAccountCredentials getClientGoogleCredentialsOrDie() {
    return getGoogleCredentialsOrDie(clientServiceAccountPath);
  }

  /** Creates {@link ClientConfig} for using CRL in test. */
  public ClientConfig getCrlClientConfig() {
    ClientConfig.Builder clientConfigBuilder =
        ClientConfig.Builder.newBuilder().setClient("terra-janitor");

    CleanupConfig cleanupConfig =
        CleanupConfig.builder()
            .setCleanupId("janitor-test")
            .setJanitorProjectId(janitorPubsubProjectId)
            .setTimeToLive(RESOURCE_TIME_TO_LIVE)
            .setJanitorTopicName(janitorTopicId)
            .setCredentials(getGoogleCredentialsOrDie(crlClientCredentialFilePath))
            .build();
    clientConfigBuilder.setCleanupConfig(cleanupConfig);
    return clientConfigBuilder.build();
  }
}
