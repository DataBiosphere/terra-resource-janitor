package bio.terra.janitor.service.primary;

import bio.terra.janitor.service.stairway.StairwayComponent;
import com.google.common.base.Preconditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The FlightScheduler is responsible for finding tracked reosurces that are ready to be cleaned up with Flights and scheduling them.
 */
@Component
public class FlightScheduler {
    /** How often to query for flights to schedule. */
    private static final Duration SCHEDULE_PERIOD = Duration.ofMinutes(1);

    /**
     * Only need as many threads as we have scheduled tasks.
     */
    private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

    private final StairwayComponent stairwayComponent;

    @Autowired
    public FlightScheduler(StairwayComponent stairwayComponent) {
        this.stairwayComponent = stairwayComponent;
    }

    /**
     * Initialize the FlightScheduler, kicking off its tasks.
     * <p>The StairwayComponent must be ready before calling this function.
     */
    public void initialize() {
        Preconditions.checkState(stairwayComponent.getStatus().equals(StairwayComponent.Status.OK), "Stairway must be ready before FlightScheduler can be initialized.");
        executor.execute(this::start);
    }

    private void start() {
        recover();
        executor.schedule(this::scheduleFlights, SCHEDULE_PERIOD.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void scheduleFlights() {
        // query dao for flights
        // for each, do submission dance
    }

    private void recover() {
      // TODO implement me.
    }


    // TODO consider termination shutdown
}
