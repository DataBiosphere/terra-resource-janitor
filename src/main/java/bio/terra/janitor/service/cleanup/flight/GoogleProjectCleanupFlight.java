package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import org.springframework.context.ApplicationContext;

/** A Flight to cleanup GCP project. */
public class GoogleProjectCleanupFlight extends Flight {
  public GoogleProjectCleanupFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    ApplicationContext appContext = (ApplicationContext) applicationContext;
    JanitorDao janitorDao = appContext.getBean(JanitorDao.class);
    CloudResourceManagerCow resourceManagerCow = appContext.getBean(CloudResourceManagerCow.class);
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);

    addStep(new InitialCleanupStep(janitorDao));
    addStep(new GoogleProjectCleanupStep(resourceManagerCow, janitorDao), retryRule);
    addStep(new FinalCleanupStep(janitorDao));
  }
}
