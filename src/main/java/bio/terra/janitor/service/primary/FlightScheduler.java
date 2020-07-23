package bio.terra.janitor.service.primary;

import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.service.stairway.StairwayComponent;
import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The FlightScheduler is responsible for finding tracked reosurces that are ready to be cleaned up
 * with Flights and scheduling them.
 */
// TODO separate into a FlightManager to hand off to stairway and a scheduler to manage the
// scheduling of the manager.
// TODO add metrics.
public class FlightScheduler {
  /** How often to query for flights to schedule. */
  private static final Duration SCHEDULE_PERIOD = Duration.ofMinutes(1);

  private Logger logger = LoggerFactory.getLogger(FlightScheduler.class);

  /** Only need as many threads as we have scheduled tasks. */
  private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

  private final StairwayComponent stairwayComponent;
  private final CleanupFlightManager cleanupFlightManager;

  public FlightScheduler(
      StairwayComponent stairwayComponent,
      JanitorDao janitorDao,
      CleanupFlightFactory cleanupFlightFactory) {
    this.stairwayComponent = stairwayComponent;
    cleanupFlightManager =
        new CleanupFlightManager(stairwayComponent.get(), janitorDao, cleanupFlightFactory);
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
    cleanupFlightManager.recoverUnsubmittedFlights();
    executor.schedule(this::scheduleFlights, SCHEDULE_PERIOD.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * Try to schedule flights to cleanup resources until there are no resources ready to be cleaned
   * up.
   */
  private void scheduleFlights() {
    // TODO add metrics.
    while (cleanupFlightManager.submitFlight().isPresent()) {}
  }
  // TODO consider termination shutdown
}
