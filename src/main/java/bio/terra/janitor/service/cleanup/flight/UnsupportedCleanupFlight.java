package bio.terra.janitor.service.cleanup.flight;

import static bio.terra.janitor.app.configuration.BeanNames.JANITOR_DAO;

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
        ((ApplicationContext) applicationContext).getBean(JANITOR_DAO, JanitorDao.class);
    addStep(new InitialCleanupStep(janitorDao));
    addStep(new UnsupportedCleanupStep());
    addStep(new FinalCleanupStep(janitorDao));
  }
}
