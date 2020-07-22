package bio.terra.janitor.service.primary;

import bio.terra.janitor.db.CleanupFlightState;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.TrackedResource;
import bio.terra.janitor.service.stairway.StairwayComponent;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightNotFoundException;
import bio.terra.stairway.exception.StairwayException;
import com.google.common.base.Preconditions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The FlightScheduler is responsible for finding tracked reosurces that are ready to be cleaned up
 * with Flights and scheduling them.
 */
// TODO separate into a FlightManager to hand off to stairway and a scheduler to manage the scheduling of the manager.
// TODO add metrics.
@Component
public class FlightScheduler {
  /** How often to query for flights to schedule. */
  private static final Duration SCHEDULE_PERIOD = Duration.ofMinutes(1);
  /**
   * A limit on how many flights to recover at startup. This is to prevent blowing out the service
   * at start up, but if we have too many flights to recover, they will not all be recovered
   * immediately.
   */
  private static final int RECOVERY_LIMIT = 1000;

  private Logger logger = LoggerFactory.getLogger(FlightScheduler.class);

  /** Only need as many threads as we have scheduled tasks. */
  private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

  private final StairwayComponent stairwayComponent;
  private final JanitorDao janitorDao;
  private final CleanupFlightFactory cleanupFlightFactory;

  @Autowired
  public FlightScheduler(
      StairwayComponent stairwayComponent,
      JanitorDao janitorDao,
      CleanupFlightFactory cleanupFlightFactory) {
    this.stairwayComponent = stairwayComponent;
    this.janitorDao = janitorDao;
    this.cleanupFlightFactory = cleanupFlightFactory;
  }

  /**
   * Initialize the FlightScheduler, kicking off its tasks.
   *
   * <p>The StairwayComponent must be ready before calling this function.
   */
  public void initialize() {
    Preconditions.checkState(
        stairwayComponent.getStatus().equals(StairwayComponent.Status.OK),
        "Stairway must be ready before FlightScheduler can be initialized.");
    executor.execute(this::start);
  }

  private void start() {
    recover();
    executor.schedule(this::scheduleFlights, SCHEDULE_PERIOD.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * Recover tracked resources with flight ids in the Janitor's storage that are not known to
   * Stairway. Resubmit the flights.
   *
   * <p>This function assumes that it is not running concurrently with any other submissions to
   * Stairway.
   */
  private void recover() {
    List<JanitorDao.TrackedResourceAndFlight> resourceAndFlights =
        janitorDao.retrieveResourcesWith(CleanupFlightState.INITIATING, RECOVERY_LIMIT);
    if (resourceAndFlights.size() == RECOVERY_LIMIT) {
      logger.error(
          "Recovering as many flights as the limit {}. Some flights may still need recovering.",
          RECOVERY_LIMIT);
    }
    for (JanitorDao.TrackedResourceAndFlight resourceAndFlight : resourceAndFlights) {
      String flightId = resourceAndFlight.cleanupFlight().flightId();
      try {
        // If there is some flight state, then the flight has been submitted successfully and does
        // not need to be recovered. There's nothing to do but wait for the flight to update it's
        // state from INITIATING.
        stairwayComponent.get().getFlightState(flightId);
      } catch (FlightNotFoundException e) {
        // Stairway does not know about the flightId, so we must not have submitted successfully.
        // Try to resubmit.
        submitFlightOrElseLog(flightId, resourceAndFlight.trackedResource());
      } catch (DatabaseOperationException | InterruptedException e) {
        logger.error(String.format("Error recovering flight id [%s]", flightId), e);
      }
    }
  }

  /**
   * Try to schedule flights to cleanup resources until there are no resources ready to be cleaned
   * up.
   */
  private void scheduleFlights() {
    // TODO add metrics.
    while (scheduleFlight()) {}
  }

  /** Schedule a single resource for cleaning. Returns whether there was a resource to schedule. */
  private boolean scheduleFlight() {
    String flightId = stairwayComponent.get().createFlightId();
    Optional<TrackedResource> resource =
        janitorDao.updateResourceForCleaning(Instant.now(), flightId);
    if (resource.isEmpty()) {
      // No resource to schedule.
      return false;
    }
    submitFlightOrElseLog(flightId, resource.get());
    return true;
  }

  /** Submits a cleanup flight for the resource to Stairway, or fails logging any exceptions. */
  private void submitFlightOrElseLog(String flightId, TrackedResource resource) {
    CleanupFlightFactory.FlightSubmission flightSubmission =
        cleanupFlightFactory.createSubmission(resource);
    try {
      stairwayComponent
          .get()
          .submitToQueue(flightId, flightSubmission.clazz(), flightSubmission.inputParameters());
    } catch (StairwayException | InterruptedException e) {
      logger.error(
          String.format(
              "Error scheduling flight for tracked_resource_id [%s]", resource.id().toString()));
    }
  }

  // TODO consider termination shutdown
}
