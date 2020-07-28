package bio.terra.janitor.service.cleanup;

import bio.terra.janitor.app.configuration.PrimaryConfiguration;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** The FlightScheduler runs the {@link FlightManager} periodically to clean resources. */
// TODO add metrics.
@Component
public class FlightScheduler {
  /** How often to query for flights to schedule. */
  private static final Duration SCHEDULE_PERIOD = Duration.ofMinutes(1);

  private Logger logger = LoggerFactory.getLogger(FlightScheduler.class);

  /** Only need as many threads as we have scheduled tasks. */
  private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

  private final PrimaryConfiguration primaryConfiguration;
  private final StairwayComponent stairwayComponent;
  private final FlightManager flightManager;

  @Autowired
  public FlightScheduler(
      PrimaryConfiguration primaryConfiguration,
      StairwayComponent stairwayComponent,
      JanitorDao janitorDao,
      FlightSubmissionFactory submissionFactory) {
    this.primaryConfiguration = primaryConfiguration;
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
    if (primaryConfiguration.isSchedulerEnabled()) {
      logger.info("Janitor scheduling enabled.");
    } else {
      // Do nothing if scheduling is disabled.
      logger.info("Janitor scheduling disabled.");
      return;
    }
    executor.execute(this::startSchedulingFlights);
  }

  private void startSchedulingFlights() {
    int numRecoveredFlights = flightManager.recoverUnsubmittedFlights();
    logger.info("Recovered {} unsubmitted flights.", numRecoveredFlights);
    // The scheduled task will not execute concurrently with itself even if it takes a long time.
    // See javadoc on ScheduledExecutorService#scheduleAtFixedRate.
    executor.scheduleAtFixedRate(
        this::scheduleFlights,
        /* initialDelay= */ 0,
        /* period= */ SCHEDULE_PERIOD.toMillis(),
        TimeUnit.MILLISECONDS);
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

  public void shutdown() {
    // Don't schedule  anything new during shutdown.
    executor.shutdown();
  }
}
