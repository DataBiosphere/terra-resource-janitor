package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import org.springframework.context.ApplicationContext;

public class AzureManagedIdentityCleanupFlight extends Flight {

  public AzureManagedIdentityCleanupFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    ApplicationContext appContext = (ApplicationContext) applicationContext;
    JanitorDao janitorDao = appContext.getBean(JanitorDao.class);
    CrlConfiguration crlConfiguration = appContext.getBean(CrlConfiguration.class);
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);

    addStep(new InitialCleanupStep(janitorDao));
    addStep(new AzureManagedIdentityCleanupStep(crlConfiguration, janitorDao), retryRule);
    addStep(new FinalCleanupStep(janitorDao));
  }
}
