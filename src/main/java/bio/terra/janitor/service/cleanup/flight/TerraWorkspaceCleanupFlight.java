package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.service.workspace.WorkspaceManagerService;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import org.springframework.context.ApplicationContext;

/** A flight to clean up a Terra Workspace, managed by Workspace Manager. */
public class TerraWorkspaceCleanupFlight extends Flight {

  public TerraWorkspaceCleanupFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    ApplicationContext appContext = (ApplicationContext) applicationContext;
    JanitorDao janitorDao = appContext.getBean(JanitorDao.class);
    WorkspaceManagerService workspaceManagerService =
        appContext.getBean(WorkspaceManagerService.class);
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);

    addStep(new InitialCleanupStep(janitorDao));
    addStep(new TerraWorkspaceCleanupStep(workspaceManagerService, janitorDao), retryRule);
    addStep(new FinalCleanupStep(janitorDao));
  }
}
