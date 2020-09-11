package bio.terra.janitor.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/** Pub/sub Configurations for tracking resource. */
@Component
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "janitor.pubsub.track-resource")
public class TrackResourcePubsubConfiguration {
  private boolean enabled;

  private String projectId;

  private String subscription;

  public boolean isEnabled() {
    return enabled;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getSubscription() {
    return subscription;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public void setSubscription(String subscription) {
    this.subscription = subscription;
  }
}
