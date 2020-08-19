package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.db.CleanupFlightState;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;

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
  public StepResult doStep(FlightContext flightContext) {
    System.out.println("~~~~~~~ ENTERING FINISHING Stage");
    System.out.println("~~~~~~~");
    janitorDao.updateFlightState(flightContext.getFlightId(), CleanupFlightState.FINISHING);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // We could set the CleanupFlightState back to IN_FLIGHT, but it doesn't mater much since the
    // Flight is finishing either way.
    return StepResult.getStepResultSuccess();
  }
}
