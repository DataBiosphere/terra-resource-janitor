package bio.terra.janitor.service.cleanup;

import bio.terra.janitor.common.CloudResourceUidVisitor;
import bio.terra.janitor.generated.model.AzureDisk;
import bio.terra.janitor.generated.model.AzureNetwork;
import bio.terra.janitor.generated.model.AzureNetworkSecurityGroup;
import bio.terra.janitor.generated.model.AzurePublicIp;
import bio.terra.janitor.generated.model.AzureVirtualMachine;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.janitor.generated.model.GoogleAiNotebookInstanceUid;
import bio.terra.janitor.generated.model.GoogleBigQueryDatasetUid;
import bio.terra.janitor.generated.model.GoogleBigQueryTableUid;
import bio.terra.janitor.generated.model.GoogleBlobUid;
import bio.terra.janitor.generated.model.GoogleBucketUid;
import bio.terra.janitor.generated.model.GoogleProjectUid;
import java.util.Optional;

/**
 * Gets {@link AzureResourceNameAndGroup} by visiting {@link CloudResourceUid}. Returns
 * Optional.empty() for non-Azure resources.
 */
public class AzureResourceNameAndGroupVisitor
    implements CloudResourceUidVisitor<Optional<AzureResourceNameAndGroup>> {
  @Override
  public Optional<AzureResourceNameAndGroup> visit(GoogleProjectUid resource) {
    return Optional.empty();
  }

  @Override
  public Optional<AzureResourceNameAndGroup> visit(GoogleBucketUid resource) {
    return Optional.empty();
  }

  @Override
  public Optional<AzureResourceNameAndGroup> visit(GoogleBlobUid resource) {
    return Optional.empty();
  }

  @Override
  public Optional<AzureResourceNameAndGroup> visit(GoogleBigQueryTableUid resource) {
    return Optional.empty();
  }

  @Override
  public Optional<AzureResourceNameAndGroup> visit(GoogleBigQueryDatasetUid resource) {
    return Optional.empty();
  }

  @Override
  public Optional<AzureResourceNameAndGroup> visit(GoogleAiNotebookInstanceUid resource) {
    return Optional.empty();
  }

  @Override
  public Optional<AzureResourceNameAndGroup> visit(AzurePublicIp resource) {
    return Optional.of(
        new AzureResourceNameAndGroup(resource.getIpName(), resource.getResourceGroup()));
  }

  @Override
  public Optional<AzureResourceNameAndGroup> visit(AzureNetworkSecurityGroup resource) {
    return Optional.of(
        new AzureResourceNameAndGroup(
            resource.getNetworkSecurityGroupName(), resource.getResourceGroup()));
  }

  @Override
  public Optional<AzureResourceNameAndGroup> visit(AzureNetwork resource) {
    return Optional.of(
        new AzureResourceNameAndGroup(resource.getNetworkName(), resource.getResourceGroup()));
  }

  @Override
  public Optional<AzureResourceNameAndGroup> visit(AzureDisk resource) {
    return Optional.of(
        new AzureResourceNameAndGroup(resource.getDiskName(), resource.getResourceGroup()));
  }

  @Override
  public Optional<AzureResourceNameAndGroup> visit(AzureVirtualMachine resource) {
    return Optional.of(
        new AzureResourceNameAndGroup(resource.getVmName(), resource.getResourceGroup()));
  }

  @Override
  public Optional<AzureResourceNameAndGroup> noResourceVisited(CloudResourceUid resource) {
    return Optional.empty();
  }

  public Optional<AzureResourceNameAndGroup> accept(CloudResourceUid cloudResourceUid) {
    return CloudResourceUidVisitor.visit(cloudResourceUid, this);
  }
}
