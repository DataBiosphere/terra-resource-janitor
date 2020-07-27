package bio.terra.janitor.service.cleanup;

import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.TrackedResource;
import bio.terra.janitor.service.cleanup.flight.FinalCleanupStep;
import bio.terra.janitor.service.cleanup.flight.InitialCleanupStep;
import bio.terra.stairway.*;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** The standard {@link FlightSubmissionFactory} to be used. */
@Component
public class FlightSubmissionFactoryImpl implements FlightSubmissionFactory {
  @Override
  public FlightSubmission createSubmission(TrackedResource trackedResource) {
    // TODO(wchamber): Add different flights and cleanup steps for each cloud resource type.
    return FlightSubmission.create(UnsupportedCleanupFlight.class, new FlightMap());
  }

  /**
   * A Flight for cleanups of resource types that are not yet supported. Always results in failure
   * by failing to cleanup the resource.
   */
  public static class UnsupportedCleanupFlight extends Flight {
    public UnsupportedCleanupFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      JanitorDao janitorDao =
          ((ApplicationContext) applicationContext).getBean("janitorDao", JanitorDao.class);
      addStep(new InitialCleanupStep(janitorDao));
      addStep(new UnimplementedCleanupStep());
      addStep(new FinalCleanupStep(janitorDao));
    }
  }

  /** A fatal step for resource types that have not been implemented yet. */
  private static class UnimplementedCleanupStep implements Step {
    @Override
    public StepResult doStep(FlightContext flightContext) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new UnsupportedOperationException("UnimplementedCleanupStep"));
    }

    @Override
    public StepResult undoStep(FlightContext flightContext) {
      return StepResult.getStepResultSuccess();
    }
  }
}
