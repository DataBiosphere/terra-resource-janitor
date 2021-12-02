package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.generated.model.AzureResourceGroup;
import bio.terra.stairway.FlightMap;
import com.azure.resourcemanager.resources.fluentcore.arm.collection.SupportsDeletingByResourceGroup;

/** Flight to clean up an Azure network security group. */
public class AzureNetworkSecurityGroupCleanupFlight extends AzureResourceCleanupFlight {
  public AzureNetworkSecurityGroupCleanupFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
  }

  @Override
  protected SupportsDeletingByResourceGroup getDeleteClient(AzureResourceGroup resourceGroup) {
    return getCrlConfiguration()
        .buildComputeManager(resourceGroup)
        .networkManager()
        .networkSecurityGroups();
  }
}
