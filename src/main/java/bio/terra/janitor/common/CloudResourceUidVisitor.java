package bio.terra.janitor.common;

import bio.terra.janitor.generated.model.*;

/** An interface for switching on the different resource types within a {@link CloudResourceUid}. */
public interface CloudResourceUidVisitor<R> {
  R visit(GoogleProjectUid resource);

  R visit(GoogleBucketUid resource);

  R visit(GoogleBlobUid resource);

  R visit(GoogleBigQueryTableUid resource);

  R visit(GoogleBigQueryDatasetUid resource);

  R visit(GoogleAiNotebookInstanceUid resource);

  R visit(AzurePublicIp resource);

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
    } else {
      return visitor.noResourceVisited(resource);
    }
  }
}
