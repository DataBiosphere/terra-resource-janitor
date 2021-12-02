package bio.terra.janitor.service.cleanup;

import bio.terra.janitor.db.ResourceType;
import bio.terra.janitor.db.ResourceTypeVisitor;
import bio.terra.janitor.db.TrackedResource;
import bio.terra.janitor.service.cleanup.flight.*;
import bio.terra.stairway.*;
import org.springframework.stereotype.Component;

/** The standard {@link FlightSubmissionFactory} to be used. */
@Component
public class FlightSubmissionFactoryImpl implements FlightSubmissionFactory {
  @Override
  public FlightSubmission createSubmission(TrackedResource trackedResource) {
    ResourceType resourceType =
        new ResourceTypeVisitor().accept(trackedResource.cloudResourceUid());
    FlightMap flightMap = new FlightMap();
    flightMap.put(FlightMapKeys.TRACKED_RESOURCE_ID, trackedResource.trackedResourceId());
    flightMap.put(FlightMapKeys.CLOUD_RESOURCE_UID, trackedResource.cloudResourceUid());
    switch (resourceType) {
      case GOOGLE_BUCKET:
        return FlightSubmission.create(GoogleBucketCleanupFlight.class, flightMap);
      case GOOGLE_BLOB:
        return FlightSubmission.create(GoogleBlobCleanupFlight.class, flightMap);
      case GOOGLE_BIGQUERY_DATASET:
        return FlightSubmission.create(GoogleBigQueryDatasetCleanupFlight.class, flightMap);
      case GOOGLE_BIGQUERY_TABLE:
        return FlightSubmission.create(GoogleBigQueryTableCleanupFlight.class, flightMap);
      case GOOGLE_NOTEBOOK_INSTANCE:
        return FlightSubmission.create(GoogleAiNotebookInstanceCleanupFlight.class, flightMap);
      case GOOGLE_PROJECT:
        return FlightSubmission.create(GoogleProjectCleanupFlight.class, flightMap);
      case AZURE_PUBLIC_IP:
        return FlightSubmission.create(AzurePublicIpCleanupFlight.class, flightMap);
      default:
        return FlightSubmission.create(UnsupportedCleanupFlight.class, new FlightMap());
    }
  }
}
