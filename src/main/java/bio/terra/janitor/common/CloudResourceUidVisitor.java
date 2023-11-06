package bio.terra.janitor.common;

import bio.terra.janitor.generated.model.AzureDatabase;
import bio.terra.janitor.generated.model.AzureDisk;
import bio.terra.janitor.generated.model.AzureKubernetesNamespace;
import bio.terra.janitor.generated.model.AzureManagedIdentity;
import bio.terra.janitor.generated.model.AzureRelayHybridConnection;
import bio.terra.janitor.generated.model.AzureStorageContainer;
import bio.terra.janitor.generated.model.AzureVirtualMachine;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.janitor.generated.model.GoogleAiNotebookInstanceUid;
import bio.terra.janitor.generated.model.GoogleBigQueryDatasetUid;
import bio.terra.janitor.generated.model.GoogleBigQueryTableUid;
import bio.terra.janitor.generated.model.GoogleBlobUid;
import bio.terra.janitor.generated.model.GoogleBucketUid;
import bio.terra.janitor.generated.model.GoogleProjectUid;
import bio.terra.janitor.generated.model.TerraWorkspaceUid;

/** An interface for switching on the different resource types within a {@link CloudResourceUid}. */
public interface CloudResourceUidVisitor<R> {
  R visit(GoogleProjectUid resource);

  R visit(GoogleBucketUid resource);

  R visit(GoogleBlobUid resource);

  R visit(GoogleBigQueryTableUid resource);

  R visit(GoogleBigQueryDatasetUid resource);

  R visit(GoogleAiNotebookInstanceUid resource);

  R visit(AzureDisk resource);

  R visit(AzureVirtualMachine resource);

  R visit(AzureRelayHybridConnection resource);

  R visit(TerraWorkspaceUid resource);

  R visit(AzureManagedIdentity resource);

  R visit(AzureStorageContainer resource);

  R visit(AzureDatabase resource);

  R visit(AzureKubernetesNamespace resource);

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
    } else if (resource.getAzureDisk() != null) {
      return visitor.visit(resource.getAzureDisk());
    } else if (resource.getAzureVirtualMachine() != null) {
      return visitor.visit(resource.getAzureVirtualMachine());
    } else if (resource.getAzureRelayHybridConnection() != null) {
      return visitor.visit(resource.getAzureRelayHybridConnection());
    } else if (resource.getTerraWorkspace() != null) {
      return visitor.visit(resource.getTerraWorkspace());
    } else if (resource.getAzureManagedIdentity() != null) {
      return visitor.visit(resource.getAzureManagedIdentity());
    } else if (resource.getAzureStorageContainer() != null) {
      return visitor.visit(resource.getAzureStorageContainer());
    } else if (resource.getAzureDatabase() != null) {
      return visitor.visit(resource.getAzureDatabase());
    } else if (resource.getAzureKubernetesNamespace() != null) {
      return visitor.visit(resource.getAzureKubernetesNamespace());
    } else {
      return visitor.noResourceVisited(resource);
    }
  }
}
