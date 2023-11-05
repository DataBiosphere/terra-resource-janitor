package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzureDisk;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.compute.models.VirtualMachine;

/** Step to clean up an Azure disk. */
public class AzureDiskCleanupStep extends ResourceCleanupStep {
  private final CrlConfiguration crlConfiguration;

  public AzureDiskCleanupStep(CrlConfiguration crlConfiguration, JanitorDao janitorDao) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzureDisk disk = resourceUid.getAzureDisk();
    ComputeManager computeManager = crlConfiguration.buildComputeManager(disk.getResourceGroup());

    return AzureUtils.ignoreNotFound(
        () -> {
          Disk resolvedDisk =
              computeManager
                  .disks()
                  .getByResourceGroup(
                      disk.getResourceGroup().getResourceGroupName(), disk.getDiskName());

          // If the disk is attached to a virtual machine, delete the VM first.
          if (resolvedDisk.isAttachedToVirtualMachine()) {
            // Resolve the VM so we can get the NICs to delete
            VirtualMachine resolvedVm =
                computeManager.virtualMachines().getById(resolvedDisk.virtualMachineId());
            // Delete the VM
            computeManager.virtualMachines().deleteById(resolvedVm.id());
            // Delete the NICs
            resolvedVm
                .networkInterfaceIds()
                .forEach(
                    nic -> computeManager.networkManager().networkInterfaces().deleteById(nic));
          }

          // Delete the disk
          computeManager.disks().deleteById(resolvedDisk.id());
        });
  }
}
