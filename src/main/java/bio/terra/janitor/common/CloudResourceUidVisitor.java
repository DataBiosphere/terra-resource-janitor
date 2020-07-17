package bio.terra.janitor.common;

import bio.terra.generated.model.*;

public interface CloudResourceUidVisitor {
  void visit(GoogleProjectUid resource);

  void visit(GoogleBucketUid resource);

  void visit(GoogleBlobUid resource);

  void visit(GoogleBigQueryTableUid resource);

  void visit(GoogleBigQueryDatasetUid resource);

  static void visit(CloudResourceUid resource, CloudResourceUidVisitor visitor) {
    if (resource.getGoogleProjectUid() != null) {
      visitor.visit(resource.getGoogleProjectUid());
    } else if (resource.getGoogleBucketUid() != null) {
      visitor.visit(resource.getGoogleBucketUid());
    } else if (resource.getGoogleBlobUid() != null) {
      visitor.visit(resource.getGoogleBlobUid());
    } else if (resource.getGoogleBigQueryDatasetUid() != null) {
      visitor.visit(resource.getGoogleBigQueryDatasetUid());
    } else if (resource.getGoogleBigQueryTableUid() != null) {
      visitor.visit(resource.getGoogleBigQueryTableUid());
    }
    // Do not process for empty resource. And it's upto the sub-Visitor on how they handle it.
  }
}
