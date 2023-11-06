package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzureBatchPool;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import com.azure.resourcemanager.batch.BatchManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to clean up an Azure Batch Pool. */
public class AzureBatchPoolCleanupStep extends ResourceCleanupStep {
  private static final Logger logger = LoggerFactory.getLogger(AzureBatchPoolCleanupStep.class);
  private final CrlConfiguration crlConfiguration;

  public AzureBatchPoolCleanupStep(CrlConfiguration crlConfiguration, JanitorDao janitorDao) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzureBatchPool pool = resourceUid.getAzureBatchPool();

    BatchManager batchManager = crlConfiguration.buildBatchManager(pool.getResourceGroup());

    logger.info("XXX deleting batch pool " + pool.getId());
    return AzureUtils.ignoreNotFound(() -> batchManager.pools().deleteById(pool.getId()));
  }
}
