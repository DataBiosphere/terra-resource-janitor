package bio.terra.janitor.service.cleanup;

import bio.terra.janitor.app.configuration.PrimaryConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceKind;
import bio.terra.janitor.db.TrackedResourceState;
import bio.terra.janitor.service.stairway.StairwayComponent;
import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** The FlightScheduler runs the {@link FlightManager} periodically to clean resources. */
@Component
public class FlightScheduler {
  private Logger logger = LoggerFactory.getLogger(FlightScheduler.class);

  /** Only need as many threads as we have scheduled tasks. */
  private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(4);

  private final PrimaryConfiguration primaryConfiguration;
  private final StairwayComponent stairwayComponent;
  private final JanitorDao janitorDao;
  private final FlightManager flightManager;
  private final MetricsHelper metricsHelper;

  @Autowired
  public FlightScheduler(
      PrimaryConfiguration primaryConfiguration,
      StairwayComponent stairwayComponent,
      JanitorDao janitorDao,
      TransactionTemplate transactionTemplate,
      FlightSubmissionFactory submissionFactory,
      MetricsHelper metricsHelper) {
    this.primaryConfiguration = primaryConfiguration;
    this.janitorDao = janitorDao;
    this.stairwayComponent = stairwayComponent;
    this.flightManager =
        new FlightManager(
            stairwayComponent.get(),
            janitorDao,
            transactionTemplate,
            submissionFactory,
            metricsHelper);
    this.metricsHelper = metricsHelper;
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
    // The scheduled task will not execute concurrently with itself even if it takes a long time.
    // See javadoc on ScheduledExecutorService#scheduleAtFixedRate.
    executor.scheduleAtFixedRate(
        new LogThrowables(this::completeFlights),
        /* initialDelay= */ 0,
        /* period= */ primaryConfiguration.getFlightCompletionPeriod().toMillis(),
        TimeUnit.MILLISECONDS);
    executor.scheduleAtFixedRate(
        new LogThrowables(this::completeFatalFlights),
        /* initialDelay= */ 0,
        /* period= */ primaryConfiguration.getFatalFlightCompletionPeriod().toMillis(),
        TimeUnit.MILLISECONDS);
    executor.scheduleAtFixedRate(
        new LogThrowables(this::recordResourceCount),
        /* initialDelay= */ 0,
        /* period= */ primaryConfiguration.getRecordResourceCountPeriod().toMillis(),
        TimeUnit.MILLISECONDS);
  }

  private void startSchedulingFlights() {
    int numRecoveredFlights =
        flightManager.recoverUnsubmittedFlights(
            primaryConfiguration.getUnsubmittedFlightRecoveryLimit());
    if (numRecoveredFlights == primaryConfiguration.getUnsubmittedFlightRecoveryLimit()) {
      // TODO add alerting for this case.
      logger.error(
          "Recovering as many flights as the limit {}. Some flights may still need recovering.",
          numRecoveredFlights);
    }
    logger.info("Recovered {} unsubmitted flights.", numRecoveredFlights);
    executor.scheduleAtFixedRate(
        new LogThrowables(this::scheduleFlights),
        /* initialDelay= */ 0,
        /* period= */ primaryConfiguration.getFlightSubmissionPeriod().toMillis(),
        TimeUnit.MILLISECONDS);
  }

  /**
   * Try to schedule flights to cleanup resources until there are no resources ready to be cleaned
   * up.
   */
  private void scheduleFlights() {
    logger.info("Beginning scheduling flights.");
    int flightsScheduled = 0;
    while (flightManager.submitFlight(JanitorDao.currentInstant()).isPresent()) {
      ++flightsScheduled;
    }
    logger.info("Done scheduling {} flights.", flightsScheduled);
  }

  private void completeFlights() {
    logger.info("Beginning completing flights.");
    int completedFlights =
        flightManager.updateCompletedFlights(primaryConfiguration.getFlightCompletionLimit());
    logger.info("Done completing {} flights.", completedFlights);
  }

  private void completeFatalFlights() {
    logger.info("Beginning completing fatal flights.");
    int completedFlights =
        flightManager.updateFatalFlights(primaryConfiguration.getFatalFlightCompletionLimit());
    logger.info("Done completing {} fatal flights.", completedFlights);
  }

  private void recordResourceCount() {
    logger.info("Beginning recording resource counts.");
    Table<ResourceKind, TrackedResourceState, Integer> counts = janitorDao.retrieveResourceCounts();
    for (var rowMapEntry : counts.rowMap().entrySet()) {
      ResourceKind kind = rowMapEntry.getKey();
      Map<TrackedResourceState, Integer> stateCounts = rowMapEntry.getValue();
      for (TrackedResourceState state : TrackedResourceState.values()) {
        // Set values for all states for each found resource kind. As the janitorDao does not return
        // 0 counts, we need to make sure to update metrics measurements that may have changed to 0.
        // If we don't, the last non-zero value will continue to be exported.
        int count = stateCounts.getOrDefault(state, 0);
        metricsHelper.recordResourceKindCount(kind, state, count);
      }
    }
    logger.info("Done recording resource counts.");
  }

  public void shutdown() {
    // Don't schedule  anything new during shutdown.
    executor.shutdown();
  }

  /**
   * Wraps a runnable to log any thrown errors to allow the runnable to still be run with a {@link
   * ScheduledExecutorService}.
   *
   * <p>ScheduledExecutorService scheduled tasks that throw errors stop executing.
   */
  private class LogThrowables implements Runnable {
    private final Runnable task;

    private LogThrowables(Runnable task) {
      this.task = task;
    }

    @Override
    public void run() {
      try {
        task.run();
      } catch (Throwable t) {
        logger.error(
            "Caught exception in FlightScheduler ScheduledExecutorService. StackTrace:\n"
                + t.getStackTrace(),
            t);
      }
    }
  }
}
