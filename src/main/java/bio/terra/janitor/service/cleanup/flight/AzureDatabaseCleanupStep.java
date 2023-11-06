package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzureDatabase;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import com.azure.resourcemanager.postgresqlflexibleserver.PostgreSqlManager;

/** Step to clean up an Azure database. */
public class AzureDatabaseCleanupStep extends ResourceCleanupStep {
  private final CrlConfiguration crlConfiguration;

  public AzureDatabaseCleanupStep(CrlConfiguration crlConfiguration, JanitorDao janitorDao) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzureDatabase database = resourceUid.getAzureDatabase();
    PostgreSqlManager postgresManager =
        crlConfiguration.buildPostgreSqlManager(database.getResourceGroup());

    return AzureUtils.ignoreNotFound(
        () ->
            postgresManager
                .databases()
                .delete(
                    database.getResourceGroup().getResourceGroupName(),
                    database.getServerName(),
                    database.getDatabaseName()));
  }
}
