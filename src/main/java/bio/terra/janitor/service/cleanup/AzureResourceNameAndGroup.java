package bio.terra.janitor.service.cleanup;

import bio.terra.janitor.generated.model.AzureResourceGroup;

/** Defines an Azure resource name and group, used for cleanup. */
public class AzureResourceNameAndGroup {
  private final String resourceName;
  private final AzureResourceGroup resourceGroup;

  public AzureResourceNameAndGroup(String resourceName, AzureResourceGroup resourceGroup) {
    this.resourceName = resourceName;
    this.resourceGroup = resourceGroup;
  }

  public String getResourceName() {
    return resourceName;
  }

  public AzureResourceGroup getResourceGroup() {
    return resourceGroup;
  }
}
