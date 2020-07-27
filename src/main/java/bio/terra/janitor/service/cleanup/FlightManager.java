package bio.terra.janitor.service.cleanup;

import bio.terra.janitor.db.*;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightNotFoundException;
import bio.terra.stairway.exception.StairwayException;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the Flights to clean up tracked resources. It queries {@link JanitorDao} and {@link
 * Stairway} to hand off work between the two.
 *
 * <p>This class is meant to be run by only the primary Janitor instance, not by the secondaries.
 *
 * <p>The handoff is done by this class and tracked in the cleanup_flight table and Stairway's
 * database.
 */
class FlightManager {
  private Logger logger = LoggerFactory.getLogger(FlightManager.class);

  /**
   * A limit on how many flights to recover at startup. This is to prevent blowing out the service
   * at start up, but if we have too many flights to recover, they will not all be recovered
   * immediately.
   */
  private static final int RECOVERY_LIMIT = 1000;

  private final Stairway stairway;
  private final JanitorDao janitorDao;
  private final FlightSubmissionFactory submissionFactory;

  public FlightManager(
      Stairway stairway, JanitorDao janitorDao, FlightSubmissionFactory submissionFactory) {
    this.stairway = stairway;
    this.janitorDao = janitorDao;
    this.submissionFactory = submissionFactory;
  }

  /**
   * Schedule a single resource for cleaning. Returns whether a flight id if a flight was attempted
   * to be submitted to Stairway.
   */
  public Optional<String> submitFlight(Instant expiredBy) {
    String flightId = stairway.createFlightId();
    Optional<TrackedResource> resource = janitorDao.updateResourceForCleaning(expiredBy, flightId);
    if (!resource.isPresent()) {
      // No resource to schedule.
      return Optional.empty();
    }
    // If submission fails, it will be recovered later.
    submitToStairway(flightId, resource.get());
    return Optional.of(flightId);
  }

  /**
   * Recover tracked resources with flight ids in the Janitor's storage that are not known to
   * Stairway. Resubmit the flights, returning how many flights were resubmitted.
   *
   * <p>This function assumes that it is not running concurrently with any other submissions to
   * Stairway, e.g {@link #submitFlight(Instant)}.
   */
  public int recoverUnsubmittedFlights() {
    List<JanitorDao.TrackedResourceAndFlight> resourceAndFlights =
        janitorDao.retrieveResourcesWith(CleanupFlightState.INITIATING, RECOVERY_LIMIT);
    if (resourceAndFlights.size() == RECOVERY_LIMIT) {
      // TODO add alerting for this case.
      logger.error(
          "Recovering as many flights as the limit {}. Some flights may still need recovering.",
          RECOVERY_LIMIT);
    }
    int submissions = 0;
    for (JanitorDao.TrackedResourceAndFlight resourceAndFlight : resourceAndFlights) {
      String flightId = resourceAndFlight.cleanupFlight().flightId();
      try {
        // If there is some flight state, then the flight has been submitted successfully and does
        // not need to be recovered. There's nothing to do but wait for the flight to update it's
        // state from INITIATING.
        // TODO(wchamber): add metrics.
        stairway.getFlightState(flightId);
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
   * Find up to {@code limit} flights that are finishing cleanup in the Janitor's storage and
   * transition their state out of cleaning as appropriate. Returns how many resources finished
   * their cleanup flights.
   *
   * <p>This function assumes that it is not running concurrently with itself.
   */
  public int updateCompletedFlights(int limit) {
    List<JanitorDao.TrackedResourceAndFlight> resourceAndFlights =
        janitorDao.retrieveResourcesWith(CleanupFlightState.FINISHING, limit);
    int completedFlights = 0;
    for (JanitorDao.TrackedResourceAndFlight resourceAndFlight : resourceAndFlights) {
      if (completeFlight(resourceAndFlight)) {
        ++completedFlights;
      }
    }
    return completedFlights;
  }

  /**
   * Completes the flight if Stairway has finished the flight non-fatally. Returns whether we were
   * able to update the state of the resource and flight successfully for the completed flight.
   */
  private boolean completeFlight(JanitorDao.TrackedResourceAndFlight resourceAndFlight) {
    String flightId = resourceAndFlight.cleanupFlight().flightId();
    FlightState flightState;
    try {
      flightState = stairway.getFlightState(flightId);
    } catch (DatabaseOperationException | InterruptedException e) {
      logger.error(String.format("Error getting state of finishing flight [%s]", flightId), e);
      return false;
    }
    TrackedResourceState endCleaningState;
    if (flightState.getFlightStatus().equals(FlightStatus.SUCCESS)) {
      endCleaningState = TrackedResourceState.DONE;
    } else if (flightState.getFlightStatus().equals(FlightStatus.ERROR)) {
      endCleaningState = TrackedResourceState.ERROR;
    } else {
      // The flight hasn't finished or has finished fatally. Let Stairway keep working or the fatal
      // monitor handle this respectively.
      return false;
    }
    try {
      updateFinishedCleanupState(
          resourceAndFlight.trackedResource().trackedResourceId(), flightId, endCleaningState);
    } catch (UnexpectedCleanupState unexpectedCleanupState) {
      logger.error(
          String.format("Error finishing flight changing cleanup state. [%s]", flightId),
          unexpectedCleanupState);
      return false;
    }
    return true;
  }

  /**
   * Transactionally update the TrackedResourceState and CleanupFlightState for the finishing
   * flight. Throws an exception if there is an unexpected state to rollback the transaction.
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  private void updateFinishedCleanupState(
      TrackedResourceId trackedResourceId, String flightId, TrackedResourceState endCleaningState)
      throws UnexpectedCleanupState {
    // Retrieve the TrackedResource within this transaction to ensure no one else is abandoning or
    // duplicating it while we finish it.
    Optional<TrackedResource> resource = janitorDao.retrieveTrackedResource(trackedResourceId);
    if (!resource.isPresent()) {
      throw new UnexpectedCleanupState(
          String.format("Unable to find tracked_resource with id [%s]", trackedResourceId));
    }
    TrackedResourceState resourceState = resource.get().trackedResourceState();
    if (resourceState.equals(TrackedResourceState.CLEANING)) {
      janitorDao.updateResourceState(trackedResourceId, endCleaningState);
      // We assume no one else is modifying the CleanupFlightState while we do this.
    } else if (!resourceState.equals(TrackedResourceState.ABANDONED)
        && !resourceState.equals(TrackedResourceState.DUPLICATED)) {
      // The resource should not have moved from CLEANING to any other state while there was a
      // flight working on it.
      throw new UnexpectedCleanupState(
          String.format("Unexpected TrackedResourceState: %s", resourceState));
    }
    janitorDao.updateFlightState(flightId, CleanupFlightState.FINISHED);
  }

  /** Exception for unexpected resource state when finishing a cleanup flight. */
  private static class UnexpectedCleanupState extends Exception {
    UnexpectedCleanupState(String message) {
      super(message);
    }
  }

  // TODO(wchamber): Add methods for finding fatal Flights.
}
