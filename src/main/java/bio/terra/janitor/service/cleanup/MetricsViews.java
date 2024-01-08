package bio.terra.janitor.service.cleanup;

import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.View;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.util.Pair;

@Configuration
public class MetricsViews {

  @Bean(name = MetricsHelper.SUBMISSION_DURATION_METER_NAME)
  public Pair<InstrumentSelector, View> submissionDurationView() {
    return Pair.of(
        InstrumentSelector.builder()
            .setMeterName(MetricsHelper.SUBMISSION_DURATION_METER_NAME)
            .build(),
        View.builder()
            .setName(MetricsHelper.SUBMISSION_DURATION_METER_NAME)
            .setDescription("Duration of a cleanup flight submission")
            .setAggregation(Aggregation.base2ExponentialBucketHistogram())
            .setAttributeFilter(Set.of(MetricsHelper.SUCCESS_KEY.getKey()))
            .build());
  }

  @Bean(name = MetricsHelper.SUBMISSION_COUNT_METER_NAME)
  public Pair<InstrumentSelector, View> submissionCountView() {
    return Pair.of(
        InstrumentSelector.builder()
            .setMeterName(MetricsHelper.SUBMISSION_COUNT_METER_NAME)
            .build(),
        View.builder()
            .setName(MetricsHelper.SUBMISSION_COUNT_METER_NAME)
            .setDescription("Counter of cleanup flight submissions")
            .setAggregation(Aggregation.sum())
            .setAttributeFilter(Set.of(MetricsHelper.SUCCESS_KEY.getKey()))
            .build());
  }

  @Bean(name = MetricsHelper.COMPLETION_DURATION_METER_NAME)
  public Pair<InstrumentSelector, View> completionDurationView() {
    return Pair.of(
        InstrumentSelector.builder()
            .setMeterName(MetricsHelper.COMPLETION_DURATION_METER_NAME)
            .build(),
        View.builder()
            .setName(MetricsHelper.COMPLETION_DURATION_METER_NAME)
            .setDescription("Duration of a cleanup flight completion")
            .setAggregation(Aggregation.base2ExponentialBucketHistogram())
            .setAttributeFilter(Set.of(MetricsHelper.SUCCESS_KEY.getKey()))
            .build());
  }

  @Bean(name = MetricsHelper.COMPLETION_COUNT_METER_NAME)
  public Pair<InstrumentSelector, View> completionCountView() {
    return Pair.of(
        InstrumentSelector.builder()
            .setMeterName(MetricsHelper.COMPLETION_COUNT_METER_NAME)
            .build(),
        View.builder()
            .setName(MetricsHelper.COMPLETION_COUNT_METER_NAME)
            .setDescription("Counter of cleanup flight completions")
            .setAggregation(Aggregation.sum())
            .setAttributeFilter(Set.of(MetricsHelper.SUCCESS_KEY.getKey()))
            .build());
  }

  @Bean(name = MetricsHelper.FATAL_UPDATE_DURATION_METER_NAME)
  public Pair<InstrumentSelector, View> fatalUpdateDurationView() {
    return Pair.of(
        InstrumentSelector.builder()
            .setMeterName(MetricsHelper.FATAL_UPDATE_DURATION_METER_NAME)
            .build(),
        View.builder()
            .setName(MetricsHelper.FATAL_UPDATE_DURATION_METER_NAME)
            .setDescription("Duration of a cleanup flight fatal update")
            .setAggregation(Aggregation.base2ExponentialBucketHistogram())
            .setAttributeFilter(Set.of(MetricsHelper.SUCCESS_KEY.getKey()))
            .build());
  }

  @Bean(name = MetricsHelper.TRACKED_RESOURCE_GAUGE_METER_NAME)
  public Pair<InstrumentSelector, View> trackedResourceGaugeView() {
    return Pair.of(
        InstrumentSelector.builder()
            .setMeterName(MetricsHelper.TRACKED_RESOURCE_GAUGE_METER_NAME)
            .build(),
        View.builder()
            .setName(MetricsHelper.TRACKED_RESOURCE_GAUGE_METER_NAME)
            .setDescription("Gauge of the current number of tracked resources")
            .setAggregation(Aggregation.lastValue())
            .setAttributeFilter(
                Set.of(
                    MetricsHelper.RESOURCE_STATE_KEY.getKey(),
                    MetricsHelper.RESOURCE_TYPE_KEY.getKey(),
                    MetricsHelper.CLIENT_KEY.getKey()))
            .build());
  }

  @Bean(name = MetricsHelper.RECOVERED_SUBMITTED_FLIGHTS_COUNT_METER_NAME)
  public Pair<InstrumentSelector, View> recoveredSubmittedFlightsCountView() {
    return Pair.of(
        InstrumentSelector.builder()
            .setMeterName(MetricsHelper.RECOVERED_SUBMITTED_FLIGHTS_COUNT_METER_NAME)
            .build(),
        View.builder()
            .setName(MetricsHelper.RECOVERED_SUBMITTED_FLIGHTS_COUNT_METER_NAME)
            .setDescription(
                "Count of the number of recovered flights that were already submitted successfully to Stairway")
            .setAggregation(Aggregation.sum())
            .build());
  }

  @Bean(name = MetricsHelper.FATAL_FLIGHT_UNDELETED_COUNT_METER_NAME)
  public Pair<InstrumentSelector, View> fatalFlightUndeletedCount() {
    return Pair.of(
        InstrumentSelector.builder()
            .setMeterName(MetricsHelper.FATAL_FLIGHT_UNDELETED_COUNT_METER_NAME)
            .build(),
        View.builder()
            .setName(MetricsHelper.FATAL_FLIGHT_UNDELETED_COUNT_METER_NAME)
            .setDescription(
                "Count of the number of fatal cleanup flights that were not deleted from Stairway when they were completed by the Janitor")
            .setAggregation(Aggregation.sum())
            .build());
  }
}
