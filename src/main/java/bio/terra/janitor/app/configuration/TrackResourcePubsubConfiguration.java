package bio.terra.janitor.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/** Pub/sub Configurations for tracking resource. */
@Component
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "pubsub.track-resource")
public class TrackResourcePubsubConfiguration {
  private boolean pubsubEnabled;

  private String projectId;

  private String subscription;

  public boolean isPubsubEnabled() {
    return pubsubEnabled;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getSubscription() {
    return subscription;
  }

  public void setPubsubEnabled(boolean pubsubEnabled) {
    this.pubsubEnabled = pubsubEnabled;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public void setSubscription(String subscription) {
    this.subscription = subscription;
  }
}
