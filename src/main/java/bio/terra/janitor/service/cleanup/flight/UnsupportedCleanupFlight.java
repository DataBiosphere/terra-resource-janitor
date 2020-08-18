package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import org.springframework.context.ApplicationContext;

/**
 * A Flight for cleanups of resource types that are not yet supported. Always results in failure by
 * failing to cleanup the resource.
 */
public class UnsupportedCleanupFlight extends Flight {
  public UnsupportedCleanupFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    JanitorDao janitorDao =
        ((ApplicationContext) applicationContext).getBean("janitorDao", JanitorDao.class);
    addStep(new InitialCleanupStep(janitorDao));
    addStep(new UnsupportedCleanupStep());
    addStep(new FinalCleanupStep(janitorDao));
  }
}
