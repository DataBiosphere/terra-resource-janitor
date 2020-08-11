package bio.terra.janitor.service.stackdriver;

import bio.terra.janitor.app.configuration.StackdriverConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A component for setting up stackdriver stats exporting. */
@Component
public class StatsExporter {
  private final Logger logger = LoggerFactory.getLogger(StatsExporter.class);

  private final StackdriverConfiguration stackdriverConfiguration;

  @Autowired
  public StatsExporter(StackdriverConfiguration stackdriverConfiguration) {
    this.stackdriverConfiguration = stackdriverConfiguration;
  }

  public void initialize() {
    logger.info("Stackdriver stats enabled: {}.", stackdriverConfiguration.isEnabled());
    if (!stackdriverConfiguration.isEnabled()) {
      return;
    }
    try {
      StackdriverStatsExporter.createAndRegister();
    } catch (IOException e) {
      throw new RuntimeException("Unable to initialize Stackdriver stats exporting.", e);
    }
  }
}
