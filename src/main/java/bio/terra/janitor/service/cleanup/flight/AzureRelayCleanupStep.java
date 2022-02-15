package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzureRelay;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import com.azure.resourcemanager.relay.RelayManager;

/** Step to clean up an Azure Relay. */
public class AzureRelayCleanupStep extends ResourceCleanupStep {
  private final CrlConfiguration crlConfiguration;

  public AzureRelayCleanupStep(CrlConfiguration crlConfiguration, JanitorDao janitorDao) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzureRelay relay = resourceUid.getAzureRelay();

    RelayManager relayManager = crlConfiguration.buildRelayManager(relay.getResourceGroup());

    return AzureUtils.ignoreNotFound(
        () ->
            relayManager
                .namespaces()
                .deleteByResourceGroup(
                    relay.getResourceGroup().getResourceGroupName(), relay.getRelayName()));
  }
}
