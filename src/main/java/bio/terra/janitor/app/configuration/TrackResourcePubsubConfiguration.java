package bio.terra.janitor.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Pub/sub Configurations for tracking resource. */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "pubsub.trackResource")
public class TrackResourcePubsubConfiguration {
    private boolean pubsubEnabled;
    private String projectId;

    private String topicId;
    private String subscription;

    public boolean isPubsubEnabled() {
        return pubsubEnabled;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getTopicId() {
        return topicId;
    }

    public String getSubscription() {
        return subscription;
    }
}
