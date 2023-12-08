package bio.terra.janitor.app;

import bio.terra.cloudres.util.MetricsHelper;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.View;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.util.Pair;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(
    basePackages = {
      "bio.terra.janitor",
      "bio.terra.common.iam",
      "bio.terra.common.migrate",
            "bio.terra.common.prometheus",
            "bio.terra.common.gcpmetrics"
    })
public class Main {
  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }

    // The next 3 beans register views from CRL.
    // It would have been nice to use a bean post processor but that did not work with generic types.
    @Bean(MetricsHelper.API_COUNT_METER_NAME)
    public Pair<InstrumentSelector, View> apiCountView() {
        return getInstrumentSelectorViewPair(MetricsHelper.API_COUNT_METER_NAME);
    }

    @Bean(MetricsHelper.LATENCY_METER_NAME)
    public Pair<InstrumentSelector, View> latencyView() {
        return getInstrumentSelectorViewPair(MetricsHelper.LATENCY_METER_NAME);
    }

    @Bean(MetricsHelper.ERROR_COUNT_METER_NAME)
    public Pair<InstrumentSelector, View> errorCountView() {
        return getInstrumentSelectorViewPair(MetricsHelper.ERROR_COUNT_METER_NAME);
    }

    private static Pair<InstrumentSelector, View> getInstrumentSelectorViewPair(String meterName) {
        return Pair.of(
                InstrumentSelector.builder().setMeterName(meterName).build(),
                MetricsHelper.getMetricsViews().get(meterName));
    }
}
