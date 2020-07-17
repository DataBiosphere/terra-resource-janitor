package bio.terra.janitor.common;

import static bio.terra.janitor.common.JanitorResourceTypeEnum.*;

import bio.terra.generated.model.*;
import bio.terra.janitor.common.exception.InvalidResourceUidException;
import org.springframework.stereotype.Component;

/**
 * Enums mapped from {@link null} to our DB schema. The original value can be found at <a
 * href="https://github.com/DataBiosphere/terra-cloud-resource-lib/blob/master/cloud-resource-schema/src/main/resources/cloud_resources_uid.yaml">terra-cloud-resource-lib
 * repo</a>
 */
@Component
public class JanitorResourceEnumVisitor implements CloudResourceUidVisitor {
  private JanitorResourceTypeEnum janitorResourceTypeEnum;

  @Override
  public void visit(GoogleProjectUid resource) {
    janitorResourceTypeEnum = GOOGLE_PROJECT;
  }

  @Override
  public void visit(GoogleBucketUid resource) {
    janitorResourceTypeEnum = GOOGLE_BUCKET;
  }

  @Override
  public void visit(GoogleBlobUid resource) {
    janitorResourceTypeEnum = GOOGLE_BLOB;
  }

  @Override
  public void visit(GoogleBigQueryTableUid resource) {
    janitorResourceTypeEnum = GOOGLE_BIG_QUERY_TABLE;
  }

  @Override
  public void visit(GoogleBigQueryDatasetUid resource) {
    janitorResourceTypeEnum = GOOGLE_BIG_QUERY_DATASET;
  }

  public JanitorResourceTypeEnum accept(CloudResourceUid cloudResourceUid) {
    // Reset the value just incase this method is called multiple times in a request.
    janitorResourceTypeEnum = null;
    CloudResourceUidVisitor.visit(cloudResourceUid, this);
    System.out.println("777777");
    System.out.println(janitorResourceTypeEnum);
    if (janitorResourceTypeEnum == null) {
      throw new InvalidResourceUidException("invalid CloudResourceUid for" + cloudResourceUid);
    }
    return janitorResourceTypeEnum;
  }
}
