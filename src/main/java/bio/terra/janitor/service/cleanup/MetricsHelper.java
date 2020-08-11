package bio.terra.janitor.service.cleanup;

import com.google.common.collect.ImmutableList;
import io.opencensus.stats.*;
import io.opencensus.tags.*;
import java.time.Duration;
import java.util.Arrays;

/** Helper class for recording metrics associated with cleanup. */
class MetricsHelper {
  private MetricsHelper() {}

  public static final String PREFIX = "terra/janitor/cleanup";
  public static final ViewManager VIEW_MANAGER = Stats.getViewManager();

  private static final Tagger TAGGER = Tags.getTagger();
  private static final StatsRecorder STATS_RECORDER = Stats.getStatsRecorder();

  private static final TagKey KEY_SUCCESS = TagKey.create("success");
  /** Unit string for millisecond. */
  private static final String MILLISECOND = "ms";

  private static final Measure.MeasureDouble SUBMISSION_DURATION =
      Measure.MeasureDouble.create(
          PREFIX + "/submission_duration", "Duration of a cleanup flight submission.", MILLISECOND);
  private static final Measure.MeasureDouble COMPLETION_DURATION =
      Measure.MeasureDouble.create(
          PREFIX + "/completion_duration", "Duration of a cleanup flight completion.", MILLISECOND);
  private static final Measure.MeasureDouble FATAL_UPDATE_DURATION =
      Measure.MeasureDouble.create(
          PREFIX + "/fatal_update_duration",
          "Duration of a cleanup flight fatal update.",
          MILLISECOND);

  /**
   * This bucketing is our first pass guess at what might be interesting to see for durations. It is
   * not backed by data.
   */
  private static final Aggregation DURATION_DISTRIBUTION =
      Aggregation.Distribution.create(
          BucketBoundaries.create(
              Arrays.asList(
                  0.0, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0, 64.0, 128.0, 256.0, 512.0, 1024.0, 2048.0,
                  4096.0, 8192.0)));

  private static final View SUBMISSION_DURATION_VIEW =
      View.create(
          View.Name.create(PREFIX + "/submission_duration"),
          "The distribution of durations for flight submissions",
          SUBMISSION_DURATION,
          DURATION_DISTRIBUTION,
          ImmutableList.of(KEY_SUCCESS));
  private static final View COMPLETION_DURATION_VIEW =
      View.create(
          View.Name.create(PREFIX + "/completion_duration"),
          "The distribution of durations for flight completions",
          COMPLETION_DURATION,
          DURATION_DISTRIBUTION,
          ImmutableList.of(KEY_SUCCESS));
  private static final View FATAL_UPDATE_DURATION_VIEW =
      View.create(
          View.Name.create(PREFIX + "/fatal_update_duration"),
          "The distribution of durations for flight fatal updates",
          FATAL_UPDATE_DURATION,
          DURATION_DISTRIBUTION,
          ImmutableList.of(KEY_SUCCESS));

  private static final ImmutableList<View> VIEWS =
      ImmutableList.of(
          SUBMISSION_DURATION_VIEW, COMPLETION_DURATION_VIEW, FATAL_UPDATE_DURATION_VIEW);

  // Register all views
  static {
    for (View view : VIEWS) {
      VIEW_MANAGER.registerView(view);
    }
  }

  /** Record the duration of an attempt to submit a cleanup flight. */
  public static void recordSubmissionDuration(Duration duration, boolean flightSubmitted) {
    TagContext tctx =
        TAGGER
            .emptyBuilder()
            .putLocal(KEY_SUCCESS, TagValue.create(Boolean.valueOf(flightSubmitted).toString()))
            .build();
    STATS_RECORDER.newMeasureMap().put(SUBMISSION_DURATION, duration.toMillis()).record(tctx);
  }

  /** Record the duration of an attempt to complete a cleanup flight. */
  public static void recordCompletionDuration(Duration duration, boolean flightCompleted) {
    TagContext tctx =
        TAGGER
            .emptyBuilder()
            .putLocal(KEY_SUCCESS, TagValue.create(Boolean.valueOf(flightCompleted).toString()))
            .build();
    STATS_RECORDER.newMeasureMap().put(COMPLETION_DURATION, duration.toMillis()).record(tctx);
  }

  /**
   * Record the duration of an attempt to update a cleanup flight that ended fatally in Stairway.
   */
  public static void recordFatalUpdateDuration(Duration duration, boolean flightUpdated) {
    TagContext tctx =
        TAGGER
            .emptyBuilder()
            .putLocal(KEY_SUCCESS, TagValue.create(Boolean.valueOf(flightUpdated).toString()))
            .build();
    STATS_RECORDER.newMeasureMap().put(FATAL_UPDATE_DURATION, duration.toMillis()).record(tctx);
  }
}
