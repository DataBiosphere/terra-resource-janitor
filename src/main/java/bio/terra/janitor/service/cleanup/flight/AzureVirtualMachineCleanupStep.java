package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzureVirtualMachine;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;

/** Step to clean up an Azure virtual machine. */
public class AzureVirtualMachineCleanupStep extends ResourceCleanupStep {
  private final CrlConfiguration crlConfiguration;

  public AzureVirtualMachineCleanupStep(CrlConfiguration crlConfiguration, JanitorDao janitorDao) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzureVirtualMachine vm = resourceUid.getAzureVirtualMachine();
    ComputeManager computeManager = crlConfiguration.buildComputeManager(vm.getResourceGroup());

    return AzureUtils.ignoreNotFound(
        () -> {
          // Resolve the VM in Azure so we can delete the nics and OS disk after the VM is deleted.
          VirtualMachine resolvedVm =
              computeManager
                  .virtualMachines()
                  .getByResourceGroup(vm.getResourceGroup().getResourceGroupName(), vm.getVmName());

          // Delete the VM
          computeManager
              .virtualMachines()
              .deleteByResourceGroup(vm.getResourceGroup().getResourceGroupName(), vm.getVmName());

          // Delete the nics
          // In the future nics will be tracked as a separate resource in CRL/Janitor.
          // See: https://broadworkbench.atlassian.net/browse/IA-3097
          resolvedVm
              .networkInterfaceIds()
              .forEach(nic -> computeManager.networkManager().networkInterfaces().deleteById(nic));

          // Delete the OS disk
          computeManager.disks().deleteById(resolvedVm.osDiskId());

          // Delete the user disks
          resolvedVm.dataDisks().values().stream()
              .forEach(disk -> computeManager.disks().deleteById(disk.id()));
        });
  }
}
