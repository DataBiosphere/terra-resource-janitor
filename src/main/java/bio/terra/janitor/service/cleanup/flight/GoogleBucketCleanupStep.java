package bio.terra.janitor.service.cleanup.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.janitor.db.TrackedResource;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import com.google.cloud.storage.StorageOptions;

public class GoogleBucketCleanupStep implements Step {
  private final StorageCow storageCow;

  public GoogleBucketCleanupStep() {
    // Janitor only uses CRL Cows to delete resources. Cleanup is not needed.
    this.storageCow =
        new StorageCow(
            ClientConfig.Builder.newBuilder().setClient("terra-crl-janitor").build(),
            StorageOptions.getDefaultInstance());
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    TrackedResource trackedResource =
        flightContext
            .getInputParameters()
            .get(FlightMapKeys.TRACKED_RESOURCE, TrackedResource.class);
    storageCow.delete(trackedResource.cloudResourceUid().getGoogleBucketUid().getBucketName());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
