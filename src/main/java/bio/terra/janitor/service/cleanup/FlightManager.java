package bio.terra.janitor.service.cleanup;

import bio.terra.janitor.db.*;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightNotFoundException;
import bio.terra.stairway.exception.StairwayException;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Manages the Flights to clean up tracked resources. It queries {@link JanitorDao} and {@link
 * Stairway} to hand off work between the two.
 *
 * <p>This class is meant to be run by only the primary Janitor instance, not by the secondaries.
 *
 * <p>The handoff is done by this class and tracked in the cleanup_flight table and Stairway's
 * database. Invariants:
 *
 * <ul>
 *   <li>A tracked resource must be in the READY state and have expired to create a cleaning flight.
 *   <li>A tracked resource has at most one "active" cleaning flight - a CleanupFlight whose state
 *       is not FINISHED or FATAL.
 *   <li>A tracked resource with an active flight must be in the CLEANING, DUPLICATED, or ABANDONED
 *       state. This cannot be enforced by this class but is relied on.
 *   <li>When a Stairway Flight begins, the cleanup flight will be in the INITIATING state.
 *   <li>The Stairway Flight is responsible for transitioning the cleaning flight state to IN_FLIGHT
 *       and FINISHING. A non-FATAL Flight must change the state to FINISHING.
 *   <li>This class is responsible for atomically the cleaning flight state to FINISHED or FATAL and
 *       updating the tracked resource state. This is only done after Stairway has finished
 *       executing the Flight.
 *   <li>If the tracked resource is DUPLICATED or ABANDONED before this class transitions the flight
 *       state to FINISHED or FATAL, the tracked resource state should not change.
 * </ul>
 */
class FlightManager {
  private Logger logger = LoggerFactory.getLogger(FlightManager.class);

  private final Stairway stairway;
  private final JanitorDao janitorDao;
  private final TransactionTemplate transactionTemplate;
  private final FlightSubmissionFactory submissionFactory;

  public FlightManager(
      Stairway stairway,
      JanitorDao janitorDao,
      TransactionTemplate transactionTemplate,
      FlightSubmissionFactory submissionFactory) {
    this.stairway = stairway;
    this.janitorDao = janitorDao;
    this.transactionTemplate = transactionTemplate;
    this.submissionFactory = submissionFactory;
  }

  /**
   * Schedule a single resource for cleaning. Returns whether a flight id if a flight was attempted
   * to be submitted to Stairway.
   */
  public Optional<String> submitFlight(Instant expiredBy) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    String flightId = stairway.createFlightId();
    Optional<TrackedResource> resource =
        transactionTemplate.execute(
            status -> updateResourceForCleaning(expiredBy, flightId, status));
    if (!resource.isPresent()) {
      // No resource to schedule.
      return Optional.empty();
    }
    // If submission fails, it will be recovered later.
    boolean submissionSuccessful = submitToStairway(flightId, resource.get());
    // Only record duration of submission if there was something to attempt to schedule.
    MetricsHelper.recordSubmissionDuration(
        Duration.ofNanos(stopwatch.elapsed(TimeUnit.NANOSECONDS)), submissionSuccessful);
    return Optional.of(flightId);
  }

  /**
   * Retrieves and updates a TrackedResource that is ready and has expired by {@code expiredBy} to
   * {@link TrackedResourceState#CLEANING}. Inserts a new {@link CleanupFlight} for that resource as
   * initiating.
   *
   * <p>This should be done as a part of a transaction. The TransactionStatus is unused, but a part
   * of the signature as a reminder.
   */
  private Optional<TrackedResource> updateResourceForCleaning(
      Instant expiredBy, String flightId, TransactionStatus unused) {
    List<TrackedResource> resources =
        janitorDao.retrieveResourcesMatching(
            TrackedResourceFilter.builder()
                .allowedStates(ImmutableSet.of(TrackedResourceState.READY))
                .expiredBy(expiredBy)
                .limit(1)
                .build());
    if (resources.isEmpty()) {
      return Optional.empty();
    }
    if (resources.size() > 1) {
      throw new IllegalStateException(
          String.format("Retrieved more than 1 resource with limit 1, %d", resources.size()));
    }
    TrackedResource resource = resources.get(0);

    janitorDao.createCleanupFlight(
        resource.trackedResourceId(),
        CleanupFlight.create(flightId, CleanupFlightState.INITIATING));
    return janitorDao.updateResourceState(
        resource.trackedResourceId(), TrackedResourceState.CLEANING);
  }

  /**
   * Recover up to {@code limit} tracked resources with flight ids in the Janitor's storage that are
   * not known to Stairway. Resubmit the flights, returning how many flights were resubmitted.
   *
   * <p>This function assumes that it is not running concurrently with any other submissions to
   * Stairway, e.g {@link #submitFlight(Instant)}.
   */
  public int recoverUnsubmittedFlights(int limit) {
    List<TrackedResourceAndFlight> resourceAndFlights =
        janitorDao.retrieveResourcesWith(CleanupFlightState.INITIATING, limit);
    int submissions = 0;
    for (TrackedResourceAndFlight resourceAndFlight : resourceAndFlights) {
      String flightId = resourceAndFlight.cleanupFlight().flightId();
      try {
        // If there is some flight state, then the flight has been submitted successfully and does
        // not need to be recovered. There's nothing to do but wait for the flight to update it's
        // state from INITIATING.
        stairway.getFlightState(flightId);
        MetricsHelper.incrementRecoveredSubmittedFlight();
      } catch (FlightNotFoundException e) {
        // Stairway does not know about the flightId, so we must not have submitted successfully.
        // Try to resubmit.
        if (submitToStairway(flightId, resourceAndFlight.trackedResource())) {
          ++submissions;
        }
      } catch (DatabaseOperationException | InterruptedException e) {
        logger.error(String.format("Error recovering flight id [%s]", flightId), e);
      }
    }
    return submissions;
  }

  /**
   * Submits a cleanup flight for the resource to Stairway, or fails logging any exceptions. Returns
   * success.
   */
  private boolean submitToStairway(String flightId, TrackedResource resource) {
    FlightSubmissionFactory.FlightSubmission flightSubmission =
        submissionFactory.createSubmission(resource);
    try {
      stairway.submitToQueue(
          flightId, flightSubmission.clazz(), flightSubmission.inputParameters());
    } catch (StairwayException | InterruptedException e) {
      logger.error(
          String.format(
              "Error scheduling flight for tracked_resource_id [%s]",
              resource.trackedResourceId().toString()),
          e);
      return false;
    }
    return true;
  }

  /**
   * Finds up to {@code limit} flights that are finishing cleanup in the Janitor's storage and
   * transition their state out of cleaning as appropriate. Returns how many resources finished
   * their cleanup flights.
   *
   * <p>This function assumes that it is not running concurrently with itself.
   */
  public int updateCompletedFlights(int limit) {
    List<TrackedResourceAndFlight> resourceAndFlights =
        janitorDao.retrieveResourcesWith(CleanupFlightState.FINISHING, limit);
    int completedFlights = 0;
    for (TrackedResourceAndFlight resourceAndFlight : resourceAndFlights) {
      Stopwatch stopwatch = Stopwatch.createStarted();
      boolean flightCompleted = completeFlight(resourceAndFlight);
      MetricsHelper.recordCompletionDuration(
          Duration.ofNanos(stopwatch.elapsed(TimeUnit.NANOSECONDS)), flightCompleted);
      if (flightCompleted) {
        ++completedFlights;
      }
    }
    return completedFlights;
  }

  /**
   * Completes the flight if Stairway has finished the flight non-fatally. Returns whether we were
   * able to update the state of the resource and flight successfully for the completed flight.
   */
  private boolean completeFlight(TrackedResourceAndFlight resourceAndFlight) {
    String flightId = resourceAndFlight.cleanupFlight().flightId();
    Optional<FlightStatus> flightStatus;
    try {
      flightStatus = Optional.of(stairway.getFlightState(flightId).getFlightStatus());
    } catch (FlightNotFoundException e) {
      logger.error(
          "Completed tracked resource flight not found. Tracked resource id [{}]. Flight id [{}].",
          resourceAndFlight.trackedResource().trackedResourceId(),
          flightId);
      flightStatus = Optional.empty();
    } catch (DatabaseOperationException | InterruptedException e) {
      logger.error(
          String.format(
              "Error getting state of finishing flight. Tracked resource id [%s]. Flight id [%s].",
              resourceAndFlight.trackedResource().trackedResourceId(), flightId),
          e);
      return false;
    }

    CompletedFlightState completedFlightState;
    if (flightStatus.isEmpty()) {
      completedFlightState = CompletedFlightState.LOST;
    } else if (flightStatus.get().equals(FlightStatus.SUCCESS)) {
      completedFlightState = CompletedFlightState.SUCCESS;
    } else if (flightStatus.get().equals(FlightStatus.ERROR)) {
      completedFlightState = CompletedFlightState.ERROR;
    } else {
      // The flight hasn't finished or has finished fatally. Let Stairway keep working or the
      // fatal monitor handle this respectively.
      return false;
    }
    return transactionTemplate.execute(
        status ->
            updateFinishedCleanupState(
                resourceAndFlight.trackedResource().trackedResourceId(),
                flightId,
                completedFlightState,
                status));
  }

  /* An enum for the possible states of a completed flight. */
  private enum CompletedFlightState {
    SUCCESS, // The flight completed successfully.
    ERROR, // The flight failed.
    LOST; // We somehow lost the flight entirely.
  }

  /**
   * Update the TrackedResourceState and CleanupFlightState for the finishing flight. Returns
   * whether the update was successful.
   *
   * <p>This should be done as a part of a transaction.
   */
  private boolean updateFinishedCleanupState(
      TrackedResourceId trackedResourceId,
      String flightId,
      CompletedFlightState completedFlightState,
      TransactionStatus transactionStatus) {
    // Retrieve the TrackedResource within this transaction to ensure no one else is abandoning or
    // duplicating it while we finish it.
    Optional<TrackedResource> resource = janitorDao.retrieveTrackedResource(trackedResourceId);
    if (!resource.isPresent()) {
      logger.error(
          "Unable to find tracked_resource while finishing flight. Tracked resource id [{}]. Flight id [{}].",
          trackedResourceId,
          flightId);
      transactionStatus.setRollbackOnly();
      return false;
    }
    TrackedResourceState resourceState = resource.get().trackedResourceState();
    if (resourceState.equals(TrackedResourceState.CLEANING)) {
      TrackedResourceState finalState;
      switch (completedFlightState) {
        case SUCCESS:
          finalState = TrackedResourceState.DONE;
          break;
        case ERROR:
          finalState = TrackedResourceState.ERROR;
          break;
        case LOST:
          // We lost the flight in some unexpected way. We pessimistically assume that the resource
          // was
          // not cleaned up.
          finalState = TrackedResourceState.ERROR;
          break;
        default:
          throw new AssertionError("Unknown CompletedFlightState.");
      }
      janitorDao.updateResourceState(trackedResourceId, finalState);

    } else if (!resourceState.equals(TrackedResourceState.ABANDONED)
        && !resourceState.equals(TrackedResourceState.DUPLICATED)) {
      // The resource should not have moved from CLEANING to any other state while there was a
      // flight working on it.
      logger.error(
          "Unexpected TrackedResourceState {} while finishing flight. Tracked resource id [{}]. Flight id [{}].",
          resourceState,
          trackedResourceId,
          flightId);
      transactionStatus.setRollbackOnly();
      return false;
    }
    // We assume no one else is modifying the CleanupFlightState while we do this.
    CleanupFlightState cleanupFlightState =
        completedFlightState.equals(CompletedFlightState.LOST)
            ? CleanupFlightState.LOST
            : CleanupFlightState.FINISHED;
    janitorDao.updateFlightState(flightId, cleanupFlightState);
    return true;
  }

  /**
   * Finds up to {@code limit} flights that are FATAL in Stairway transition their state out of
   * cleaning as appropriate. Returns how many resources finished their cleanup flights.
   *
   * <p>This function assumes that it is not running concurrently with itself.
   */
  public int updateFatalFlights(int limit) {
    FlightFilter flightFilter =
        new FlightFilter().addFilterFlightStatus(FlightFilterOp.EQUAL, FlightStatus.FATAL);
    List<FlightState> flights;
    try {
      flights = stairway.getFlights(/* offset =*/ 0, limit, flightFilter);
    } catch (DatabaseOperationException | InterruptedException e) {
      logger.error("Error getting FATAL flights.", e);
      return 0;
    }
    int completedFlights = 0;
    for (FlightState flight : flights) {
      Stopwatch stopwatch = Stopwatch.createStarted();
      boolean flightCompleted = updateFatalFlight(flight.getFlightId());
      MetricsHelper.recordFatalUpdateDuration(
          Duration.ofNanos(stopwatch.elapsed(TimeUnit.NANOSECONDS)), flightCompleted);
      if (flightCompleted) {
        ++completedFlights;
      }
    }
    return completedFlights;
  }

  private boolean updateFatalFlight(String flightId) {
    if (!transactionTemplate.execute(status -> updateFatalCleanupState(flightId, status))) {
      return false;
    }
    try {
      // We delete the FATAL flights from Stairway once they've been processed so that we can
      // continue to query Stairway for FATAL flights efficiently.
      stairway.deleteFlight(flightId, /* forceDelete =*/ false);
    } catch (DatabaseOperationException | InterruptedException e) {
      // Even though Stairway failed to delete the flight, the Janitor considers it cleaned up. We
      // will try to delete the flight from Stairway on another go around.
      logger.error(String.format("Error deleting flight [%s]", flightId), e);
      return false;
    }
    return true;
  }

  /**
   * Update the TrackedResourceState and CleanupFlightState for the fatal flight. Returns whether
   * the update was successful.
   *
   * <p>This should be done as a part of a transaction.
   */
  private boolean updateFatalCleanupState(String flightId, TransactionStatus transactionStatus) {
    Optional<TrackedResourceAndFlight> resourceAndFlight =
        janitorDao.retrieveResourceAndFlight(flightId);
    if (!resourceAndFlight.isPresent()) {
      logger.error(
          "Unable to find tracked_resource for flight id {} while finishing fatal.", flightId);
      transactionStatus.setRollbackOnly();
      return false;
    }
    TrackedResource trackedResource = resourceAndFlight.get().trackedResource();
    TrackedResourceState resourceState = trackedResource.trackedResourceState();
    if (resourceAndFlight.get().cleanupFlight().state().equals(CleanupFlightState.FATAL)) {
      // We already marked the flight as completed, we must have previously failed to delete the
      // flight from Stairway. We should try the Stairway deletion again.
      MetricsHelper.incrementFatalFlightUndeleted();
      return true;
    }
    if (resourceState.equals(TrackedResourceState.CLEANING)) {
      janitorDao.updateResourceState(
          trackedResource.trackedResourceId(), TrackedResourceState.ERROR);
    } else if (!resourceState.equals(TrackedResourceState.ABANDONED)
        && !resourceState.equals(TrackedResourceState.DUPLICATED)) {
      logger.error(
          "Unexpected TrackedResourceState {} while finishing fatal flight. Tracked resource id [{}]. Flight id [{}].",
          resourceState,
          trackedResource.trackedResourceId(),
          resourceAndFlight.get().cleanupFlight().flightId());
      transactionStatus.setRollbackOnly();
      return false;
    }
    janitorDao.updateFlightState(flightId, CleanupFlightState.FATAL);
    return true;
  }
}
