package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import org.springframework.context.ApplicationContext;

/** Flight to clean up an Azure database. */
public class AzureKubernetesNamespaceCleanupFlight extends Flight {
  public AzureKubernetesNamespaceCleanupFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    ApplicationContext appContext = (ApplicationContext) applicationContext;
    JanitorDao janitorDao = appContext.getBean(JanitorDao.class);
    CrlConfiguration crlConfiguration = appContext.getBean(CrlConfiguration.class);
    KubernetesClientProvider kubernetesClientProvider =
        appContext.getBean(KubernetesClientProvider.class);
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);

    addStep(new InitialCleanupStep(janitorDao));
    addStep(
        new AzureKubernetesNamespaceCleanupStep(
            crlConfiguration, janitorDao, kubernetesClientProvider),
        retryRule);
    addStep(new FinalCleanupStep(janitorDao));
  }
}
