package bio.terra.janitor.service.stackdriver;

import bio.terra.janitor.app.configuration.StackdriverConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A component for setting up Stackdriver stats & tracing exporting. */
@Component
public class StatsExporter {
  private final Logger logger = LoggerFactory.getLogger(StatsExporter.class);

  private final StackdriverConfiguration stackdriverConfiguration;

  @Autowired
  public StatsExporter(StackdriverConfiguration stackdriverConfiguration) {
    this.stackdriverConfiguration = stackdriverConfiguration;
  }

  public void initialize() {
    logger.info("Stackdriver enabled: {}.", stackdriverConfiguration.isEnabled());
    if (!stackdriverConfiguration.isEnabled()) {
      return;
    }
    try {
      StackdriverStatsExporter.createAndRegister();
      StackdriverTraceExporter.createAndRegister(StackdriverTraceConfiguration.builder().build());
    } catch (IOException e) {
      throw new RuntimeException("Unable to initialize Stackdriver exporting.", e);
    }

    TraceConfig globalTraceConfig = Tracing.getTraceConfig();
    globalTraceConfig.updateActiveTraceParams(
        globalTraceConfig
            .getActiveTraceParams()
            .toBuilder()
            .setSampler(
                Samplers.probabilitySampler(stackdriverConfiguration.getTraceSampleProbability()))
            .build());
  }
}
