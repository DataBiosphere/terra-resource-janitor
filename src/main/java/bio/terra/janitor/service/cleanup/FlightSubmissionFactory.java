package bio.terra.janitor.service.cleanup;

import bio.terra.janitor.db.TrackedResource;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import com.google.auto.value.AutoValue;

/**
 * An interface for creating the parameters needed to submit a {@link Flight} to Stairway to cleanup
 * a {@link TrackedResource}.
 */
public interface FlightSubmissionFactory {
  FlightSubmission createSubmission(TrackedResource trackedResource);

  /** A value class of the parameters needed to submit a new flight to Stairway. */
  @AutoValue
  abstract class FlightSubmission {
    public abstract Class<? extends Flight> clazz();

    public abstract FlightMap inputParameters();

    public static FlightSubmission create(
        Class<? extends Flight> clazz, FlightMap inputParameters) {
      return new AutoValue_FlightSubmissionFactory_FlightSubmission(clazz, inputParameters);
    }
  }
}
