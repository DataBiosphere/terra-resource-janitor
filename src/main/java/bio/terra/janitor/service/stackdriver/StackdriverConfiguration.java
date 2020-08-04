package bio.terra.janitor.service.stackdriver;

import io.opencensus.exporter.stats.stackdriver.StackdriverStatsConfiguration;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "stackdriver")
public class StackdriverConfiguration {
  private boolean enabled = false;

  private String projectId;

  private Duration statsExportInterval = Duration.ofSeconds(20);

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public void setStatsExportInterval(Duration statsExportInterval) {
        this.statsExportInterval = statsExportInterval;
    }

    /**
   * Returns a {@link StackdriverStatsConfiguration} to configure Stackdriver stats if they should
   * be exported.
   */
  public Optional<StackdriverStatsConfiguration> createStatsConfiguration() {
    if (!enabled) {
      return Optional.empty();
    }
    return Optional.of(
        StackdriverStatsConfiguration.builder()
            .setProjectId(projectId)
            .setExportInterval(
                io.opencensus.common.Duration.fromMillis(statsExportInterval.toMillis()))
            .build());
  }
}
