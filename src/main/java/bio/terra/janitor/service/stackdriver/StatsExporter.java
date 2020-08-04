package bio.terra.janitor.service.stackdriver;

import io.opencensus.exporter.stats.stackdriver.StackdriverStatsConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StatsExporter {
  private final Logger logger = LoggerFactory.getLogger(StatsExporter.class);

  private final StackdriverConfiguration stackdriverConfiguration;

  @Autowired
  public StatsExporter(StackdriverConfiguration stackdriverConfiguration) {
    this.stackdriverConfiguration = stackdriverConfiguration;
  }

  public void initialize() {
    Optional<StackdriverStatsConfiguration> statsConfiguration =
        stackdriverConfiguration.createStatsConfiguration();
    if (!statsConfiguration.isPresent()) {
      logger.info("Stackdriver stats disabled.");
      return;
    }
    try {
      StackdriverStatsExporter.createAndRegister(statsConfiguration.get());
    } catch (IOException e) {
      throw new RuntimeException("Unable to initialize Stackdriver stats exporting.", e);
    }
  }
}
