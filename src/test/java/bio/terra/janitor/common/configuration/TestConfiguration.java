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
  private String prodTrackResourceTopicId;
  private String prodCrlClientCredentialFilePath;

  private String resourceCredentialFilePath;
  private String prodTrackResourceProjectId;

  public void setProdCrlClientCredentialFilePath(String prodCrlClientCredentialFilePath) {
    this.prodCrlClientCredentialFilePath = prodCrlClientCredentialFilePath;
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

  /** Creates {@link ClientConfig} for using CRL in test. */
  public ClientConfig getCrlClientConfig() {
    ClientConfig.Builder clientConfigBuilder =
        ClientConfig.Builder.newBuilder().setClient("terra-janitor");

    CleanupConfig cleanupConfig =
        CleanupConfig.builder()
            .setCleanupId("janitor-test")
            .setJanitorProjectId(prodTrackResourceProjectId)
            .setTimeToLive(RESOURCE_TIME_TO_LIVE)
            .setJanitorTopicName(prodTrackResourceTopicId)
            .setCredentials(getGoogleCredentialsOrDie(prodCrlClientCredentialFilePath))
            .build();
    clientConfigBuilder.setCleanupConfig(cleanupConfig);
    return clientConfigBuilder.build();
  }

  /**
   * {@link ServiceAccountCredentials} which has permission to publish message to Janitor prod
   * instance.
   */
  public ServiceAccountCredentials getClientGoogleCredentialsOrDie() {
    return getGoogleCredentialsOrDie(prodCrlClientCredentialFilePath);
  }

  /**
   * Janitor Client {@link ServiceAccountCredentials} which has permission to access cloud resources
   * in test.
   */
  public ServiceAccountCredentials getResourceAccessGoogleCredentialsOrDie() {
    return getGoogleCredentialsOrDie(resourceCredentialFilePath);
  }
}
