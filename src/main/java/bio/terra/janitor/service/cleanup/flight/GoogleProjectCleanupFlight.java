package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import org.springframework.context.ApplicationContext;

import static bio.terra.janitor.app.configuration.BeanNames.CRL_CLIENT_CONFIG;
import static bio.terra.janitor.app.configuration.BeanNames.JANITOR_DAO;

/** A Flight to cleanup GCP project. */
public class GoogleProjectCleanupFlight extends Flight {
  public GoogleProjectCleanupFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    JanitorDao janitorDao =
        ((ApplicationContext) applicationContext).getBean(JANITOR_DAO, JanitorDao.class);
    ClientConfig clientConfig =
        ((ApplicationContext) applicationContext).getBean(CRL_CLIENT_CONFIG, ClientConfig.class);
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);

    addStep(new InitialCleanupStep(janitorDao));
    addStep(new GoogleProjectCleanupStep(clientConfig, janitorDao), retryRule);
    addStep(new FinalCleanupStep(janitorDao));
  }
}
