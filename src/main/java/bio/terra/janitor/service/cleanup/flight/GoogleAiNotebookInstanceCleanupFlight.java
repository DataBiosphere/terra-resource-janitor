package bio.terra.janitor.service.cleanup.flight;

import static bio.terra.janitor.app.configuration.BeanNames.CRL_CLIENT_CONFIG;
import static bio.terra.janitor.app.configuration.BeanNames.JANITOR_DAO;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import org.springframework.context.ApplicationContext;

/** A Flight to cleanup an AI Notebook instance. */
public class GoogleAiNotebookInstanceCleanupFlight extends Flight {
  public GoogleAiNotebookInstanceCleanupFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    JanitorDao janitorDao =
        ((ApplicationContext) applicationContext).getBean(JANITOR_DAO, JanitorDao.class);
    ClientConfig clientConfig =
        ((ApplicationContext) applicationContext).getBean(CRL_CLIENT_CONFIG, ClientConfig.class);
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);

    addStep(new InitialCleanupStep(janitorDao));
    addStep(new GoogleAiNotebookInstanceCleanupStep(clientConfig, janitorDao), retryRule);
    addStep(new FinalCleanupStep(janitorDao));
  }
}
