package bio.terra.janitor.db;

import static bio.terra.janitor.db.ResourceType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.janitor.generated.model.*;
import bio.terra.janitor.common.BaseUnitTest;
import bio.terra.janitor.common.exception.InvalidResourceUidException;
import org.junit.jupiter.api.Test;

public class ResourceTypeVisitorTest extends BaseUnitTest {
  private ResourceTypeVisitor visitor = new ResourceTypeVisitor();

  @Test
  public void acceptGoogleProject() {
    assertEquals(
        GOOGLE_PROJECT,
        visitor.accept(
            new CloudResourceUid()
                .googleProjectUid(new GoogleProjectUid().projectId("my-project"))));
  }

  @Test
  public void acceptGoogleBigQueryDataset() {
    assertEquals(
        GOOGLE_BIGQUERY_DATASET,
        visitor.accept(
            new CloudResourceUid()
                .googleBigQueryDatasetUid(
                    new GoogleBigQueryDatasetUid()
                        .projectId("my-project")
                        .datasetId("my-dataset"))));
  }

  @Test
  public void acceptGoogleBigQueryTable() {
    assertEquals(
        GOOGLE_BIGQUERY_TABLE,
        visitor.accept(
            new CloudResourceUid()
                .googleBigQueryTableUid(
                    new GoogleBigQueryTableUid()
                        .projectId("my-project")
                        .datasetId("my-dataset")
                        .tableId("my-table"))));
  }

  @Test
  public void acceptGoogleBlob() {
    assertEquals(
        GOOGLE_BLOB,
        visitor.accept(
            new CloudResourceUid()
                .googleBlobUid(new GoogleBlobUid().bucketName("my-bucket").blobName("my-blob"))));
  }

  @Test
  public void acceptGoogleBucket() {
    assertEquals(
        GOOGLE_BUCKET,
        visitor.accept(
            new CloudResourceUid().googleBucketUid(new GoogleBucketUid().bucketName("my-bucket"))));
  }

  @Test
  public void acceptEmpty() {
    assertThrows(InvalidResourceUidException.class, () -> visitor.accept(new CloudResourceUid()));
  }
}
