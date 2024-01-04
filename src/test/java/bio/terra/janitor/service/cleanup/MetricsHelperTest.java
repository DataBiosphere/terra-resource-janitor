package bio.terra.janitor.service.cleanup;

import static bio.terra.janitor.service.cleanup.MetricsHelper.CLIENT_KEY;
import static bio.terra.janitor.service.cleanup.MetricsHelper.COMPLETION_DURATION_METER_NAME;
import static bio.terra.janitor.service.cleanup.MetricsHelper.FATAL_FLIGHT_UNDELETED_COUNT_METER_NAME;
import static bio.terra.janitor.service.cleanup.MetricsHelper.FATAL_UPDATE_DURATION_METER_NAME;
import static bio.terra.janitor.service.cleanup.MetricsHelper.RECOVERED_SUBMITTED_FLIGHTS_COUNT_METER_NAME;
import static bio.terra.janitor.service.cleanup.MetricsHelper.RESOURCE_STATE_KEY;
import static bio.terra.janitor.service.cleanup.MetricsHelper.RESOURCE_TYPE_KEY;
import static bio.terra.janitor.service.cleanup.MetricsHelper.SUBMISSION_DURATION_METER_NAME;
import static bio.terra.janitor.service.cleanup.MetricsHelper.TRACKED_RESOURCE_COUNT_METER_NAME;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.janitor.common.BaseUnitTest;
import bio.terra.janitor.db.ResourceKind;
import bio.terra.janitor.db.ResourceType;
import bio.terra.janitor.db.TrackedResourceState;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Test for {@link bio.terra.cloudres.util.MetricsHelper} */
@Tag("unit")
public class MetricsHelperTest extends BaseUnitTest {
  private static final Duration METRICS_COLLECTION_INTERVAL = Duration.ofMillis(10);
  private MetricsHelper metricsHelper;
  private TestMetricExporter testMetricExporter;

  @BeforeEach
  void setup() {
    testMetricExporter = new TestMetricExporter();
    metricsHelper = new MetricsHelper(openTelemetry(testMetricExporter));
  }

  @Test
  public void testRecordSubmissionDuration() {
    testHistogram(
        d -> metricsHelper.recordSubmissionDuration(d, true), SUBMISSION_DURATION_METER_NAME);
  }

  @Test
  public void testRecordCompletionDuration() {
    testHistogram(
        d -> metricsHelper.recordCompletionDuration(d, true), COMPLETION_DURATION_METER_NAME);
  }

  @Test
  public void testRecordFatalUpdateDuration() {
    testHistogram(
        d -> metricsHelper.recordFatalUpdateDuration(d, true), FATAL_UPDATE_DURATION_METER_NAME);
  }

  @Test
  public void testRecordResourceKindCount() {
    var resourceKind = ResourceKind.create("client", ResourceType.GOOGLE_BUCKET);
    var trackedResourceState = TrackedResourceState.READY;
    metricsHelper.recordResourceKindCount(resourceKind, trackedResourceState, 100);
    var metricData = waitForMetrics(testMetricExporter, METRICS_COLLECTION_INTERVAL);
    assertEquals(TRACKED_RESOURCE_COUNT_METER_NAME, metricData.getName());
    assertEquals(1, metricData.getLongSumData().getPoints().size());
    var point = metricData.getLongSumData().getPoints().iterator().next();
    assertEquals(100, point.getValue());
    assertEquals(trackedResourceState.toString(), point.getAttributes().get(RESOURCE_STATE_KEY));
    assertEquals(
        resourceKind.resourceType().toString(), point.getAttributes().get(RESOURCE_TYPE_KEY));
    assertEquals(resourceKind.client(), point.getAttributes().get(CLIENT_KEY));
  }

  @Test
  public void testIncrementRecoveredSubmittedFlight() {
    metricsHelper.incrementRecoveredSubmittedFlight();
    var metricData = waitForMetrics(testMetricExporter, METRICS_COLLECTION_INTERVAL);
    assertEquals(RECOVERED_SUBMITTED_FLIGHTS_COUNT_METER_NAME, metricData.getName());
    assertEquals(1, metricData.getLongSumData().getPoints().size());
    var point = metricData.getLongSumData().getPoints().iterator().next();
    assertEquals(1, point.getValue());
  }

  @Test
  public void testIncrementFatalFlightUndeleted() {
    metricsHelper.incrementFatalFlightUndeleted();
    var metricData = waitForMetrics(testMetricExporter, METRICS_COLLECTION_INTERVAL);
    assertEquals(FATAL_FLIGHT_UNDELETED_COUNT_METER_NAME, metricData.getName());
    assertEquals(1, metricData.getLongSumData().getPoints().size());
    var point = metricData.getLongSumData().getPoints().iterator().next();
    assertEquals(1, point.getValue());
  }

  private void testHistogram(Consumer<Duration> recordMetric, String name) {
    var duration = Duration.of(5, ChronoUnit.MINUTES);
    recordMetric.accept(duration);
    var metricData = waitForMetrics(testMetricExporter, METRICS_COLLECTION_INTERVAL);

    assertEquals(name, metricData.getName());

    assertEquals(1, metricData.getHistogramData().getPoints().size());
    assertEquals(
        duration.toMillis(), metricData.getHistogramData().getPoints().iterator().next().getSum());
  }

  private static MetricData waitForMetrics(
      TestMetricExporter testMetricExporter, Duration pollInterval) {
    return waitForMetrics(testMetricExporter, pollInterval, 1).iterator().next();
  }

  private static Collection<MetricData> waitForMetrics(
      TestMetricExporter testMetricExporter, Duration pollInterval, int expectedMetricsCount) {
    await()
        .atMost(pollInterval.multipliedBy(10))
        .pollInterval(pollInterval)
        .until(
            () ->
                testMetricExporter.getLastMetrics() != null
                    && testMetricExporter.getLastMetrics().size() == expectedMetricsCount);
    return testMetricExporter.getLastMetrics();
  }

  public OpenTelemetry openTelemetry(TestMetricExporter testMetricExporter) {
    var sdkMeterProviderBuilder =
        SdkMeterProvider.builder()
            .registerMetricReader(
                PeriodicMetricReader.builder(testMetricExporter)
                    .setInterval(METRICS_COLLECTION_INTERVAL)
                    .build());

    return OpenTelemetrySdk.builder().setMeterProvider(sdkMeterProviderBuilder.build()).build();
  }
}
