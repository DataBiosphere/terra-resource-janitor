package bio.terra.janitor.service.cleanup;

import bio.terra.janitor.db.TrackedResourceId;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.HookAction;
import bio.terra.stairway.StairwayHook;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** {@link StairwayHook} to add log info for cleanup flight. */
public class CleanupLoggingHook implements StairwayHook {
  private static final String FLIGHT_LOG_FORMAT =
      "Operation: {}, flightClass: {}, flightId: {}, timestamp: {}";
  private static final String StepLogFormat =
      "Operation: {}, flightClass: {}, flightId: {}, stepIndex: {}," + "timestamp: {}";
  private static final String TRACKED_RESOURCE_MDC_KEY = "trackedResourceId";
  private static final Logger logger = LoggerFactory.getLogger(CleanupLoggingHook.class);

  @Override
  public HookAction startFlight(FlightContext context) {
    logger.info(
        FLIGHT_LOG_FORMAT,
        "startFlight",
        context.getFlightClassName(),
        context.getFlightId(),
        Instant.now().atZone(ZoneId.of("Z")).format(DateTimeFormatter.ISO_INSTANT));
    return HookAction.CONTINUE;
  }

  @Override
  public HookAction startStep(FlightContext context) {
    logger.info(
        StepLogFormat,
        "startStep",
        context.getFlightClassName(),
        context.getFlightId(),
        context.getStepIndex(),
        Instant.now().atZone(ZoneId.of("Z")).format(DateTimeFormatter.ISO_INSTANT));
    TrackedResourceId trackedResourceId =
        context
            .getInputParameters()
            .get(FlightMapKeys.TRACKED_RESOURCE_ID, TrackedResourceId.class);
    if (trackedResourceId != null) {
      MDC.put(TRACKED_RESOURCE_MDC_KEY, trackedResourceId.toString());
    }
    return HookAction.CONTINUE;
  }

  @Override
  public HookAction endFlight(FlightContext context) {
    logger.info(
        FLIGHT_LOG_FORMAT,
        "endFlight",
        context.getFlightClassName(),
        context.getFlightId(),
        Instant.now().atZone(ZoneId.of("Z")).format(DateTimeFormatter.ISO_INSTANT));
    return HookAction.CONTINUE;
  }

  @Override
  public HookAction endStep(FlightContext context) {
    logger.info(
        StepLogFormat,
        "endStep",
        context.getFlightClassName(),
        context.getFlightId(),
        context.getStepIndex(),
        Instant.now().atZone(ZoneId.of("Z")).format(DateTimeFormatter.ISO_INSTANT));
    return HookAction.CONTINUE;
  }
}
