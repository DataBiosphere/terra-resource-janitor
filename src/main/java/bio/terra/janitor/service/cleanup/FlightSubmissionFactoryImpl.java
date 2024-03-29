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
      case AZURE_DISK:
        return FlightSubmission.create(AzureDiskCleanupFlight.class, flightMap);
      case AZURE_VIRTUAL_MACHINE:
        return FlightSubmission.create(AzureVirtualMachineCleanupFlight.class, flightMap);
      case AZURE_RELAY_CONNECTION:
        return FlightSubmission.create(AzureRelayHybridConnectionCleanupFlight.class, flightMap);
      case TERRA_WORKSPACE:
        return FlightSubmission.create(TerraWorkspaceCleanupFlight.class, flightMap);
      case AZURE_MANAGED_IDENTITY:
        return FlightSubmission.create(AzureManagedIdentityCleanupFlight.class, flightMap);
      case AZURE_STORAGE_CONTAINER:
        return FlightSubmission.create(AzureStorageContainerCleanupFlight.class, flightMap);
      case AZURE_DATABASE:
        return FlightSubmission.create(AzureDatabaseCleanupFlight.class, flightMap);
      case AZURE_KUBERNETES_NAMESPACE:
        return FlightSubmission.create(AzureKubernetesNamespaceCleanupFlight.class, flightMap);
      case AZURE_BATCH_POOL:
        return FlightSubmission.create(AzureBatchPoolCleanupFlight.class, flightMap);
      default:
        return FlightSubmission.create(UnsupportedCleanupFlight.class, new FlightMap());
    }
  }
}
