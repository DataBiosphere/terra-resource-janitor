package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import org.springframework.context.ApplicationContext;

/** A Flight to cleanup Blob in GCP. */
public class GoogleBlobCleanupFlight extends Flight {
  public GoogleBlobCleanupFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    ApplicationContext appContext = (ApplicationContext) applicationContext;
    JanitorDao janitorDao = appContext.getBean(JanitorDao.class);
    StorageCow storageCow = appContext.getBean(StorageCow.class);
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);

    addStep(new InitialCleanupStep(janitorDao));
    addStep(new GoogleBlobCleanupStep(storageCow, janitorDao), retryRule);
    addStep(new FinalCleanupStep(janitorDao));
  }
}
