package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.generated.model.AzureResourceGroup;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import com.azure.resourcemanager.resources.fluentcore.arm.collection.SupportsDeletingByResourceGroup;
import org.springframework.context.ApplicationContext;

/**
 * Abstract flight to clean up an Azure resource. Subclasses need to provide the {@link
 * SupportsDeletingByResourceGroup} implementation for the resource type.
 */
public abstract class AzureResourceCleanupFlight extends Flight {
  protected AzureResourceCleanupFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    ApplicationContext appContext = (ApplicationContext) applicationContext;
    JanitorDao janitorDao = appContext.getBean(JanitorDao.class);
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);
    addStep(new InitialCleanupStep(janitorDao));
    addStep(new AzureResourceCleanupStep(janitorDao, this::getDeleteClient), retryRule);
    addStep(new FinalCleanupStep(janitorDao));
  }

  protected final CrlConfiguration getCrlConfiguration() {
    ApplicationContext appContext = (ApplicationContext) getApplicationContext();
    CrlConfiguration crlConfiguration = appContext.getBean(CrlConfiguration.class);
    return crlConfiguration;
  }

  protected abstract SupportsDeletingByResourceGroup getDeleteClient(
      AzureResourceGroup resourceGroup);
}
