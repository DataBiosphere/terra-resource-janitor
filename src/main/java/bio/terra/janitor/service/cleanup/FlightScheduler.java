package bio.terra.janitor.service.cleanup;

import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.service.stairway.StairwayComponent;
import com.google.common.base.Preconditions;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The FlightScheduler is responsible for finding tracked reosurces that are ready to be cleaned up
 * with Flights and scheduling them.
 */
// TODO add metrics.
public class FlightScheduler {
  /** How often to query for flights to schedule. */
  private static final Duration SCHEDULE_PERIOD = Duration.ofMinutes(1);

  private Logger logger = LoggerFactory.getLogger(FlightScheduler.class);

  /** Only need as many threads as we have scheduled tasks. */
  private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

  private final StairwayComponent stairwayComponent;
  private final FlightManager flightManager;

  public FlightScheduler(
      StairwayComponent stairwayComponent, JanitorDao janitorDao, FlightSubmissionFactory submissionFactory) {
    this.stairwayComponent = stairwayComponent;
    flightManager = new FlightManager(stairwayComponent.get(), janitorDao, submissionFactory);
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
    executor.execute(this::startSchedulingFlights);
  }

  private void startSchedulingFlights() {
    int numRecoveredFlights = flightManager.recoverUnsubmittedFlights();
    logger.info("Recovered {} unsubmitted flights.", numRecoveredFlights);
    executor.schedule(this::scheduleFlights, SCHEDULE_PERIOD.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * Try to schedule flights to cleanup resources until there are no resources ready to be cleaned
   * up.
   */
  private void scheduleFlights() {
    logger.info("Beginning scheduling flights.");
    int flightsScheduled = 0;
    while (flightManager.submitFlight(Instant.now()).isPresent()) {
      ++flightsScheduled;
    }
    logger.info("Done scheduling {} flights.", flightsScheduled);
  }
  // TODO consider termination shutdown
}
