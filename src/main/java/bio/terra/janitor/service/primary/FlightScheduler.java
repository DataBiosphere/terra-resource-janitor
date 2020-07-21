package bio.terra.janitor.service.primary;

import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.TrackedResource;
import bio.terra.janitor.service.stairway.StairwayComponent;
import com.google.common.base.Preconditions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The FlightScheduler is responsible for finding tracked reosurces that are ready to be cleaned up
 * with Flights and scheduling them.
 */
@Component
public class FlightScheduler {
  /** How often to query for flights to schedule. */
  private static final Duration SCHEDULE_PERIOD = Duration.ofMinutes(1);
  /** A limit on how many flights to schedule at once. */
  private static final int SCHEDULE_FLIGHT_LIMIT = 100;

  /** Only need as many threads as we have scheduled tasks. */
  private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

  private final StairwayComponent stairwayComponent;
  private final JanitorDao janitorDao;

  @Autowired
  public FlightScheduler(StairwayComponent stairwayComponent, JanitorDao janitorDao) {
    this.stairwayComponent = stairwayComponent;
    this.janitorDao = janitorDao;
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

  private void recover() {
    // TODO implement me.
  }

  private void scheduleFlights() {
    // TODO repeat until none?
    // We expect this thread to never be run concurrently.
    List<TrackedResource> resources =
        janitorDao.retrieveSchedulableResources(Instant.now(), SCHEDULE_FLIGHT_LIMIT);
    for (TrackedResource resource : resources) {
      String flightId = stairwayComponent.get().createFlightId();
      janitorDao.initiateFlight(resource.id(), flightId);
      stairwayComponent.get().submitToQueue(flightId);
      // er what if the flight finishes before we update its initiating state? should the flight do
      // that?
    }
    // query dao for flights
    // for each, do submission dance
  }

  // TODO consider termination shutdown
}
