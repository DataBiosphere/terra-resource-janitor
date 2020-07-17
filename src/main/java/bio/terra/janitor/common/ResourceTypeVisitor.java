package bio.terra.janitor.common;

import static bio.terra.janitor.common.ResourceType.*;

import bio.terra.generated.model.*;
import bio.terra.janitor.common.exception.InvalidResourceUidException;
import org.springframework.stereotype.Component;

/** Gets {@link ResourceType} by visiting {@link CloudResourceUid}. */
@Component
public class ResourceTypeVisitor implements CloudResourceUidVisitor<ResourceType> {
  private ResourceType resourceType;

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
    return GOOGLE_BIG_QUERY_TABLE;
  }

  @Override
  public ResourceType visit(GoogleBigQueryDatasetUid resource) {
    return GOOGLE_BIG_QUERY_DATASET;
  }

  @Override
  public ResourceType noResourceVisited(CloudResourceUid resource) {
    throw new InvalidResourceUidException("invalid CloudResourceUid for" + resource);
  }

  public ResourceType accept(CloudResourceUid cloudResourceUid) {
    return CloudResourceUidVisitor.visit(cloudResourceUid, this);
  }
}
