package bio.terra.janitor.common;

import bio.terra.janitor.generated.model.AzureDisk;
import bio.terra.janitor.generated.model.AzureNetwork;
import bio.terra.janitor.generated.model.AzureNetworkSecurityGroup;
import bio.terra.janitor.generated.model.AzurePublicIp;
import bio.terra.janitor.generated.model.AzureRelay;
import bio.terra.janitor.generated.model.AzureRelayHybridConnection;
import bio.terra.janitor.generated.model.AzureVirtualMachine;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.janitor.generated.model.GoogleAiNotebookInstanceUid;
import bio.terra.janitor.generated.model.GoogleBigQueryDatasetUid;
import bio.terra.janitor.generated.model.GoogleBigQueryTableUid;
import bio.terra.janitor.generated.model.GoogleBlobUid;
import bio.terra.janitor.generated.model.GoogleBucketUid;
import bio.terra.janitor.generated.model.GoogleProjectUid;

/** An interface for switching on the different resource types within a {@link CloudResourceUid}. */
public interface CloudResourceUidVisitor<R> {
  R visit(GoogleProjectUid resource);

  R visit(GoogleBucketUid resource);

  R visit(GoogleBlobUid resource);

  R visit(GoogleBigQueryTableUid resource);

  R visit(GoogleBigQueryDatasetUid resource);

  R visit(GoogleAiNotebookInstanceUid resource);

  R visit(AzurePublicIp resource);

  R visit(AzureNetworkSecurityGroup resource);

  R visit(AzureNetwork resource);

  R visit(AzureDisk resource);

  R visit(AzureVirtualMachine resource);

  R visit(AzureRelay resource);

  R visit(AzureRelayHybridConnection resource);

  R noResourceVisited(CloudResourceUid resource);

  static <R> R visit(CloudResourceUid resource, CloudResourceUidVisitor<R> visitor) {
    if (resource.getGoogleProjectUid() != null) {
      return visitor.visit(resource.getGoogleProjectUid());
    } else if (resource.getGoogleBucketUid() != null) {
      return visitor.visit(resource.getGoogleBucketUid());
    } else if (resource.getGoogleBlobUid() != null) {
      return visitor.visit(resource.getGoogleBlobUid());
    } else if (resource.getGoogleBigQueryDatasetUid() != null) {
      return visitor.visit(resource.getGoogleBigQueryDatasetUid());
    } else if (resource.getGoogleBigQueryTableUid() != null) {
      return visitor.visit(resource.getGoogleBigQueryTableUid());
    } else if (resource.getGoogleAiNotebookInstanceUid() != null) {
      return visitor.visit(resource.getGoogleAiNotebookInstanceUid());
    } else if (resource.getAzurePublicIp() != null) {
      return visitor.visit(resource.getAzurePublicIp());
    } else if (resource.getAzureNetworkSecurityGroup() != null) {
      return visitor.visit(resource.getAzureNetworkSecurityGroup());
    } else if (resource.getAzureNetwork() != null) {
      return visitor.visit(resource.getAzureNetwork());
    } else if (resource.getAzureDisk() != null) {
      return visitor.visit(resource.getAzureDisk());
    } else if (resource.getAzureVirtualMachine() != null) {
      return visitor.visit(resource.getAzureVirtualMachine());
    } else if (resource.getAzureRelay() != null) {
      return visitor.visit(resource.getAzureRelay());
    } else if (resource.getAzureRelayHybridConnection() != null) {
      return visitor.visit(resource.getAzureRelayHybridConnection());
    } else {
      return visitor.noResourceVisited(resource);
    }
  }
}
