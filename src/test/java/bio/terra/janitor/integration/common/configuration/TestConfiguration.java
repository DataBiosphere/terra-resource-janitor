package bio.terra.janitor.integration.common.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configurations in integration test. */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "pubsub.trackResource")
public class TestConfiguration {
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
