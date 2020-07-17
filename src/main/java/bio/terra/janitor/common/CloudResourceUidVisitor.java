package bio.terra.janitor.common;

import bio.terra.generated.model.*;

import java.util.Optional;

/** An interface for switching on the different resource types within a {@link CloudResourceUid}. */
public interface CloudResourceUidVisitor<R> {
        R visit(GoogleProjectUid resource);

        R visit(GoogleBucketUid resource);

        R visit(GoogleBlobUid resource);

        R visit(GoogleBigQueryTableUid resource);

        R visit(GoogleBigQueryDatasetUid resource);

        R noResourceVisited(CloudResourceUid resource);

  static <R> Optional<R> visit(CloudResourceUid resource, CloudResourceUidVisitor<R> visitor) {
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
    } else {
      visitor.noResourceVisited(resource);
    }
  }
}
