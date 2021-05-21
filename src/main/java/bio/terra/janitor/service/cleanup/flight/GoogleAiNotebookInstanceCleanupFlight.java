package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
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
    ApplicationContext appContext = (ApplicationContext) applicationContext;
    JanitorDao janitorDao = appContext.getBean(JanitorDao.class);
    AIPlatformNotebooksCow notebooksCow = appContext.getBean(AIPlatformNotebooksCow.class);
    CloudResourceManagerCow resourceManagerCow = appContext.getBean(CloudResourceManagerCow.class);
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 30, /* maxCount =*/ 5);

    addStep(new InitialCleanupStep(janitorDao));
    addStep(
        new GoogleAiNotebookInstanceCleanupStep(notebooksCow, resourceManagerCow, janitorDao),
        retryRule);
    addStep(new FinalCleanupStep(janitorDao));
  }
}
