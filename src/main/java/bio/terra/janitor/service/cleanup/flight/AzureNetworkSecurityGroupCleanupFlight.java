package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import org.springframework.context.ApplicationContext;

/** Flight to clean up an Azure network security group. */
public class AzureNetworkSecurityGroupCleanupFlight extends Flight {
  public AzureNetworkSecurityGroupCleanupFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    ApplicationContext appContext = (ApplicationContext) applicationContext;
    JanitorDao janitorDao = appContext.getBean(JanitorDao.class);
    CrlConfiguration crlConfiguration = appContext.getBean(CrlConfiguration.class);
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);

    addStep(new InitialCleanupStep(janitorDao));
    addStep(new AzureNetworkSecurityGroupCleanupStep(crlConfiguration, janitorDao), retryRule);
    addStep(new FinalCleanupStep(janitorDao));
  }
}
