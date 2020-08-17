package bio.terra.janitor.integration.common.configuration;

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

  /** pubsub project id to publish track resource to Janitor test env(toolsalpha) */
  private String resourceProjectId;

  /** pubsub project id to publish track resource to Janitor prod env(tools) */
  private String prodTrackResourceProjectId;

  /** pubsub topic id to publish track resource to Janitor test env(toolsalpha) */
  private String trackResourceTopicId;

  /** pubsub topic id to publish track resource to Janitor prod env(tools) */
  private String prodTrackResourceTopicId;

  /** Credential file path to be able to pubish message to Janitor. */
  private String janitorClientCredentialFilePath;

  private String resourceCredentialFilePath;

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

  public void setJanitorClientCredentialFilePath(String janitorClientCredentialFilePath) {
    this.janitorClientCredentialFilePath = janitorClientCredentialFilePath;
  }

  public void setProdTrackResourceProjectId(String prodTrackResourceProjectId) {
    this.prodTrackResourceProjectId = prodTrackResourceProjectId;
  }

  public String getResourceProjectId() {
    return resourceProjectId;
  }

  public void setResourceProjectId(String resourceProjectId) {
    this.resourceProjectId = resourceProjectId;
  }

  public String getProdTrackResourceTopicId() {
    return prodTrackResourceTopicId;
  }

  public void setProdTrackResourceTopicId(String prodTrackResourceTopicId) {
    this.prodTrackResourceTopicId = prodTrackResourceTopicId;
  }

  public void setResourceCredentialFilePath(String resourceCredentialFilePath) {
    this.resourceCredentialFilePath = resourceCredentialFilePath;
  }

  /**
   * Janitor Client {@link ServiceAccountCredentials} which has permission to publish message to
   * Janitor.
   */
  public ServiceAccountCredentials getClientGoogleCredentialsOrDie() {
    return getGoogleCredentialsOrDie(clientServiceAccountPath);
  }

  /** Creates {@link ClientConfig} for using CRL in test. */
  public ClientConfig createClientConfig() {
    ClientConfig.Builder clientConfigBuilder =
        ClientConfig.Builder.newBuilder().setClient("terra-janitor");

    CleanupConfig cleanupConfig =
        CleanupConfig.builder()
            .setCleanupId("janitor-test")
            .setJanitorProjectId(prodTrackResourceProjectId)
            .setTimeToLive(RESOURCE_TIME_TO_LIVE)
            .setJanitorTopicName(prodTrackResourceTopicId)
            .setCredentials(getGoogleCredentialsOrDie(janitorClientCredentialFilePath))
            .build();
    clientConfigBuilder.setCleanupConfig(cleanupConfig);
    return clientConfigBuilder.build();
  }

  /**
   * Janitor Client {@link ServiceAccountCredentials} which has permission to access cloud resources
   * in test.
   */
  public ServiceAccountCredentials getResourceAccessGoogleCredentialsOrDie() {
    return getGoogleCredentialsOrDie(resourceCredentialFilePath);
  }
}
