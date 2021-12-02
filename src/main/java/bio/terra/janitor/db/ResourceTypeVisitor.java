package bio.terra.janitor.db;

import static bio.terra.janitor.db.ResourceType.AZURE_DISK;
import static bio.terra.janitor.db.ResourceType.AZURE_NETWORK;
import static bio.terra.janitor.db.ResourceType.AZURE_NETWORK_SECURITY_GROUP;
import static bio.terra.janitor.db.ResourceType.AZURE_PUBLIC_IP;
import static bio.terra.janitor.db.ResourceType.AZURE_VIRTUAL_MACHINE;
import static bio.terra.janitor.db.ResourceType.GOOGLE_BIGQUERY_DATASET;
import static bio.terra.janitor.db.ResourceType.GOOGLE_BIGQUERY_TABLE;
import static bio.terra.janitor.db.ResourceType.GOOGLE_BLOB;
import static bio.terra.janitor.db.ResourceType.GOOGLE_BUCKET;
import static bio.terra.janitor.db.ResourceType.GOOGLE_NOTEBOOK_INSTANCE;
import static bio.terra.janitor.db.ResourceType.GOOGLE_PROJECT;

import bio.terra.janitor.common.CloudResourceUidVisitor;
import bio.terra.janitor.common.exception.InvalidResourceUidException;
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

/** Gets {@link ResourceType} by visiting {@link CloudResourceUid}. */
public class ResourceTypeVisitor implements CloudResourceUidVisitor<ResourceType> {
  @Override
  public ResourceType visit(GoogleProjectUid resource) {
    return GOOGLE_PROJECT;
  }

  @Override
  public ResourceType visit(GoogleBucketUid resource) {
    return GOOGLE_BUCKET;
  }

  @Override
  public ResourceType visit(GoogleBlobUid resource) {
    return GOOGLE_BLOB;
  }

  @Override
  public ResourceType visit(GoogleBigQueryTableUid resource) {
    return GOOGLE_BIGQUERY_TABLE;
  }

  @Override
  public ResourceType visit(GoogleBigQueryDatasetUid resource) {
    return GOOGLE_BIGQUERY_DATASET;
  }

  @Override
  public ResourceType visit(GoogleAiNotebookInstanceUid resource) {
    return GOOGLE_NOTEBOOK_INSTANCE;
  }

  @Override
  public ResourceType visit(AzurePublicIp resource) {
    return AZURE_PUBLIC_IP;
  }

  @Override
  public ResourceType visit(AzureNetworkSecurityGroup resource) {
    return AZURE_NETWORK_SECURITY_GROUP;
  }

  @Override
  public ResourceType visit(AzureNetwork resource) {
    return AZURE_NETWORK;
  }

  @Override
  public ResourceType visit(AzureDisk resource) {
    return AZURE_DISK;
  }

  @Override
  public ResourceType visit(AzureVirtualMachine resource) {
    return AZURE_VIRTUAL_MACHINE;
  }

  @Override
  public ResourceType noResourceVisited(CloudResourceUid resource) {
    throw new InvalidResourceUidException("invalid CloudResourceUid for" + resource);
  }

  public ResourceType accept(CloudResourceUid cloudResourceUid) {
    return CloudResourceUidVisitor.visit(cloudResourceUid, this);
  }
}
