package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import org.springframework.context.ApplicationContext;

/** A Flight to cleanup BigQuery Dataset in GCP. */
public class GoogleDatasetCleanupFlight extends Flight {
  public GoogleDatasetCleanupFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    System.out.println("11111111111111");
    JanitorDao janitorDao =
        ((ApplicationContext) applicationContext).getBean("janitorDao", JanitorDao.class);
    ClientConfig clientConfig =
        ((ApplicationContext) applicationContext).getBean("crlClientConfig", ClientConfig.class);
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);

    addStep(new InitialCleanupStep(janitorDao));
    addStep(new GoogleDatasetCleanupStep(clientConfig, janitorDao), retryRule);
    addStep(new FinalCleanupStep(janitorDao));
  }
}
