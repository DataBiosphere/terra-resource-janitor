package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzureRelayHybridConnection;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import com.azure.resourcemanager.relay.RelayManager;

/** Step to clean up an Azure Relay. */
public class AzureRelayHybridConnectionCleanupStep extends ResourceCleanupStep {
  private final CrlConfiguration crlConfiguration;

  public AzureRelayHybridConnectionCleanupStep(
      CrlConfiguration crlConfiguration, JanitorDao janitorDao) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzureRelayHybridConnection hc = resourceUid.getAzureRelayHybridConnection();

    RelayManager relayManager = crlConfiguration.buildRelayManager(hc.getResourceGroup());

    return AzureUtils.ignoreNotFound(
        () ->
            relayManager
                .hybridConnections()
                .delete(
                    hc.getResourceGroup().getResourceGroupName(),
                    hc.getNamespace(),
                    hc.getHybridConnectionName()));
  }
}
