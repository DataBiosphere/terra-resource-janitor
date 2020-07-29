package bio.terra.janitor.service.cleanup.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;

/** A {@link Step} that causes a flight to end in a dismal fatal failure. */
public class FatalStep implements Step {

  @Override
  public StepResult doStep(FlightContext flightContext) {
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // By failing to do & undo, Stairway will be forced to have a dismal fatal failure.
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }
}
