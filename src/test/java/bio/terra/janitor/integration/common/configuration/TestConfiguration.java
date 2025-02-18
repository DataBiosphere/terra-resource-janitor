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
@ConfigurationProperties(prefix = "janitor.test")
public class TestConfiguration {
  /** How long to keep the resource before the 'prod' Janitor do the cleanup. */
  public static Duration RESOURCE_TIME_TO_LIVE_PROD = Duration.ofMinutes(30);

  /** pubsub project id to publish track resource to Janitor test env(qa) */
  private String resourceProjectId;

  /** pubsub project id to publish track resource to Janitor prod env(tools) */
  private String prodTrackResourceProjectId;

  /** pubsub topic id to publish track resource to Janitor test env(qa) */
  private String trackResourceTopicId;

  /** pubsub topic id to publish track resource to Janitor prod env(tools) */
  private String prodTrackResourceTopicId;

  /** Credential file path to be able to publish message to Janitor test env (qa). */
  private String janitorClientServiceAccountPath;

  /** Credential file path to be able to publish message to Janitor prod env (tools). */
  private String prodJanitorClientCredentialFilePath;

  /** Credential file path for accessing cloud resources. */
  private String resourceCredentialFilePath;

  /** ID of the parent folder to create projects within. */
  private String parentResourceId;

  public String getTrackResourceTopicId() {
    return trackResourceTopicId;
  }

  public void setTrackResourceTopicId(String trackResourceTopicId) {
    this.trackResourceTopicId = trackResourceTopicId;
  }

  public String getClientServiceAccountPath() {
    return janitorClientServiceAccountPath;
  }

  public void setJanitorClientServiceAccountPath(String janitorClientServiceAccountPath) {
    this.janitorClientServiceAccountPath = janitorClientServiceAccountPath;
  }

  public void setProdJanitorClientCredentialFilePath(String prodJanitorClientCredentialFilePath) {
    this.prodJanitorClientCredentialFilePath = prodJanitorClientCredentialFilePath;
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

  public String getParentResourceId() {
    return parentResourceId;
  }

  public void setParentResourceId(String parentResourceId) {
    this.parentResourceId = parentResourceId;
  }

  /**
   * Janitor Client {@link ServiceAccountCredentials} which has permission to publish message to
   * Janitor.
   */
  public ServiceAccountCredentials getClientGoogleCredentialsOrDie() {
    return getGoogleCredentialsOrDie(janitorClientServiceAccountPath);
  }

  /** Creates {@link ClientConfig} for using CRL in test. */
  public ClientConfig createClientConfig() {
    ClientConfig.Builder clientConfigBuilder =
        ClientConfig.Builder.newBuilder().setClient("terra-janitor");

    // Resources created during tests should be tracked by the Prod Janitor so that they are tracked
    // permanently and deleted even if the test fails.
    CleanupConfig cleanupConfig =
        CleanupConfig.builder()
            .setCleanupId("janitor-test")
            .setJanitorProjectId(prodTrackResourceProjectId)
            .setTimeToLive(RESOURCE_TIME_TO_LIVE_PROD)
            .setJanitorTopicName(prodTrackResourceTopicId)
            .setCredentials(getGoogleCredentialsOrDie(prodJanitorClientCredentialFilePath))
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
