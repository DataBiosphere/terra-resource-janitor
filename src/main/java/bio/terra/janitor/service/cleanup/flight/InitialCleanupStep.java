package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.db.CleanupFlightState;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;

/**
 * The initial step to run as a part of any cleanup flight. Modifies the cleanup_flight table to
 * signal starting/ending.
 */
public class InitialCleanupStep implements Step {
  private final JanitorDao janitorDao;

  public InitialCleanupStep(JanitorDao janitorDao) {
    this.janitorDao = janitorDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    janitorDao.setFlightState(flightContext.getFlightId(), CleanupFlightState.IN_FLIGHT);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Since this is the initial step, if it's being undone we are at the end of the undo chain and
    // the Flight is finishing.
    janitorDao.setFlightState(flightContext.getFlightId(), CleanupFlightState.FINISHING);
    return StepResult.getStepResultSuccess();
  }
}
