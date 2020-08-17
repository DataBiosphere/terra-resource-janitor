package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import org.springframework.context.ApplicationContext;

/** A Flight to cleanup a GoogleBucket. */
public class GoogleBucketCleanupFlight extends Flight {
  public GoogleBucketCleanupFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    JanitorDao janitorDao =
        ((ApplicationContext) applicationContext).getBean("janitorDao", JanitorDao.class);
    ClientConfig clientConfig =
        ((ApplicationContext) applicationContext).getBean("crlClientConfig", ClientConfig.class);
    addStep(new InitialCleanupStep(janitorDao));
    // Retry every 180 seconds for 5 times.
    RetryRuleFixedInterval retryRule = new RetryRuleFixedInterval(180, 5);

    addStep(new GoogleBucketCleanupStep(clientConfig, janitorDao), retryRule);
    addStep(new FinalCleanupStep(janitorDao));
  }
}
