package bio.terra.janitor.db;

import static bio.terra.janitor.db.ResourceType.*;

import bio.terra.janitor.common.CloudResourceUidVisitor;
import bio.terra.janitor.common.exception.InvalidResourceUidException;
import bio.terra.janitor.generated.model.*;

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
  public ResourceType noResourceVisited(CloudResourceUid resource) {
    throw new InvalidResourceUidException("invalid CloudResourceUid for" + resource);
  }

  public ResourceType accept(CloudResourceUid cloudResourceUid) {
    return CloudResourceUidVisitor.visit(cloudResourceUid, this);
  }
}
