package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.generated.model.AzureResourceGroup;
import bio.terra.stairway.FlightMap;
import com.azure.resourcemanager.resources.fluentcore.arm.collection.SupportsDeletingByResourceGroup;

/** Flight to clean up an Azure public IP. */
public class AzurePublicIpCleanupFlight extends AzureResourceCleanupFlight {
  public AzurePublicIpCleanupFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
  }

  @Override
  protected SupportsDeletingByResourceGroup getDeleteClient(AzureResourceGroup resourceGroup) {
    return getCrlConfiguration()
        .buildComputeManager(resourceGroup)
        .networkManager()
        .publicIpAddresses();
  }
}