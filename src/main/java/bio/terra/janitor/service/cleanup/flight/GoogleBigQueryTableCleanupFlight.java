package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import org.springframework.context.ApplicationContext;

/** A Flight to cleanup BigQuery Table in GCP. */
public class GoogleBigQueryTableCleanupFlight extends Flight {
  public GoogleBigQueryTableCleanupFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    ApplicationContext appContext = (ApplicationContext) applicationContext;
    JanitorDao janitorDao = appContext.getBean(JanitorDao.class);
    BigQueryCow bigQueryCow = appContext.getBean(BigQueryCow.class);
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);

    addStep(new InitialCleanupStep(janitorDao));
    addStep(new GoogleBigQueryTableCleanupStep(bigQueryCow, janitorDao), retryRule);
    addStep(new FinalCleanupStep(janitorDao));
  }
}
