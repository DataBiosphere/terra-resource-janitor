package bio.terra.janitor.service.cleanup;

import bio.terra.janitor.db.ResourceKind;
import bio.terra.janitor.db.TrackedResourceState;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Helper class for recording metrics associated with cleanup. */
@Component
public class MetricsHelper {
  private static final String PREFIX = "terra/janitor/cleanup";
  public static final String SUBMISSION_DURATION_METER_NAME = PREFIX + "/submission_duration";
  public static final String COMPLETION_DURATION_METER_NAME = PREFIX + "/completion_duration";
  public static final String FATAL_UPDATE_DURATION_METER_NAME = PREFIX + "/fatal_update_duration";
  public static final String TRACKED_RESOURCE_COUNT_METER_NAME = PREFIX + "/tracked_resource_count";
  public static final String RECOVERED_SUBMITTED_FLIGHTS_COUNT_METER_NAME =
      PREFIX + "/recovered_submitted_flights_count";
  public static final String FATAL_FLIGHT_UNDELETED_COUNT_METER_NAME =
      PREFIX + "/fatal_flight_undeleted_count";

  public static final AttributeKey<String> SUCCESS_KEY = AttributeKey.stringKey("success");
  public static final AttributeKey<String> RESOURCE_STATE_KEY =
      AttributeKey.stringKey("resource_state");
  public static final AttributeKey<String> RESOURCE_TYPE_KEY =
      AttributeKey.stringKey("resource_type");
  public static final AttributeKey<String> CLIENT_KEY = AttributeKey.stringKey("client");

  /** Unit string for millisecond. */
  private static final String MILLISECOND = "ms";
  /** Unit string for count. */
  private static final String COUNT = "1";

  private final DoubleHistogram submissionDuration;
  private final DoubleHistogram completionDuration;
  private final DoubleHistogram fatalUpdateDuration;
  private final LongCounter trackedResourceCount;
  private final LongCounter recoveredSubmittedFlightsCount;
  private final LongCounter fatalFlightUndeletedCount;

  public MetricsHelper(OpenTelemetry openTelemetry) {
    var meter = openTelemetry.getMeter(bio.terra.common.stairway.MetricsHelper.class.getName());
    this.submissionDuration =
        meter
            .histogramBuilder(SUBMISSION_DURATION_METER_NAME)
            .setDescription("Duration of a cleanup flight submission.")
            .setUnit(MILLISECOND)
            .build();
    this.completionDuration =
        meter
            .histogramBuilder(COMPLETION_DURATION_METER_NAME)
            .setDescription("Duration of a cleanup flight completion.")
            .setUnit(MILLISECOND)
            .build();
    this.fatalUpdateDuration =
        meter
            .histogramBuilder(FATAL_UPDATE_DURATION_METER_NAME)
            .setDescription("Duration of a cleanup flight fatal update.")
            .setUnit(MILLISECOND)
            .build();
    this.trackedResourceCount =
        meter
            .counterBuilder(TRACKED_RESOURCE_COUNT_METER_NAME)
            .setDescription("Counts of the number of tracked resources.")
            .setUnit(COUNT)
            .build();
    this.recoveredSubmittedFlightsCount =
        meter
            .counterBuilder(RECOVERED_SUBMITTED_FLIGHTS_COUNT_METER_NAME)
            .setDescription(
                "Count of the number of recovered flights that were already submitted successfully to Stairway.")
            .setUnit(COUNT)
            .build();
    this.fatalFlightUndeletedCount =
        meter
            .counterBuilder(FATAL_FLIGHT_UNDELETED_COUNT_METER_NAME)
            .setDescription(
                "Count of the number of fatal cleanup flights that were not deleted from Stairway when they were completed by the Janitor.")
            .setUnit(COUNT)
            .build();
  }

  /** Record the duration of an attempt to submit a cleanup flight. */
  public void recordSubmissionDuration(Duration duration, boolean flightSubmitted) {
    Attributes attributes = Attributes.of(SUCCESS_KEY, Boolean.valueOf(flightSubmitted).toString());
    submissionDuration.record(duration.toMillis(), attributes);
  }

  /** Record the duration of an attempt to complete a cleanup flight. */
  public void recordCompletionDuration(Duration duration, boolean flightCompleted) {
    Attributes attributes = Attributes.of(SUCCESS_KEY, Boolean.valueOf(flightCompleted).toString());
    completionDuration.record(duration.toMillis(), attributes);
  }

  /**
   * Record the duration of an attempt to update a cleanup flight that ended fatally in Stairway.
   */
  public void recordFatalUpdateDuration(Duration duration, boolean flightUpdated) {
    Attributes attributes = Attributes.of(SUCCESS_KEY, Boolean.valueOf(flightUpdated).toString());
    fatalUpdateDuration.record(duration.toMillis(), attributes);
  }

  /** Records the latest count of {@link ResourceKind}. */
  public void recordResourceKindCount(ResourceKind kind, TrackedResourceState state, int count) {
    Attributes attributes =
        Attributes.of(
            RESOURCE_STATE_KEY, state.toString(),
            RESOURCE_TYPE_KEY, kind.resourceType().toString(),
            CLIENT_KEY, kind.client());
    trackedResourceCount.add(count, attributes);
  }

  /**
   * Increment the count of the cleanup flights that were already submitted to Stairway but on
   * recovery were still in the {@link bio.terra.janitor.db.CleanupFlightState#INITIATING} state.
   */
  public void incrementRecoveredSubmittedFlight() {
    recoveredSubmittedFlightsCount.add(1);
  }

  /**
   * Increment the count of the cleanup flights that finished as {@link
   * bio.terra.janitor.db.CleanupFlightState#FATAL} but were not yet deleted from Stairway.
   */
  public void incrementFatalFlightUndeleted() {
    fatalFlightUndeletedCount.add(1);
  }
}
