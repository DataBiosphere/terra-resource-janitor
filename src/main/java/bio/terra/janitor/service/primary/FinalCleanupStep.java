package bio.terra.janitor.service.primary;

import bio.terra.janitor.db.CleanupFlightState;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;

/**
 * The final step to run as a part of any cleanup flight. Modifies the cleanup flight table to
 * signal ending.
 */
public class FinalCleanupStep implements Step {
  private final JanitorDao janitorDao;

  public FinalCleanupStep(JanitorDao janitorDao) {
    this.janitorDao = janitorDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    janitorDao.setFlightState(flightContext.getFlightId(), CleanupFlightState.FINISHING);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // We could set the CleanupFlightState back to IN_FLIGHT, but it doesn't mater much since the
    // Flight is finishing either way.
    return StepResult.getStepResultSuccess();
  }
}
