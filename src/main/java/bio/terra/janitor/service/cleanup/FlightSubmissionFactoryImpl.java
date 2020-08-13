package bio.terra.janitor.service.cleanup;

import bio.terra.janitor.common.ResourceType;
import bio.terra.janitor.common.ResourceTypeVisitor;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.TrackedResource;
import bio.terra.janitor.service.cleanup.flight.*;
import bio.terra.stairway.*;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** The standard {@link FlightSubmissionFactory} to be used. */
@Component
public class FlightSubmissionFactoryImpl implements FlightSubmissionFactory {
  @Override
  public FlightSubmission createSubmission(TrackedResource trackedResource) {
    ResourceType resourceType =
        new ResourceTypeVisitor().accept(trackedResource.cloudResourceUid());
    FlightMap flightMap = new FlightMap();
    flightMap.put(FlightMapKeys.TRACKED_RESOURCE, trackedResource);
    switch (resourceType) {
      case GOOGLE_BUCKET:
        return FlightSubmission.create(GoogleBucketCleanupFlight.class, flightMap);
      default:
        return FlightSubmission.create(UnsupportedCleanupFlight.class, new FlightMap());
    }
  }

  /**
   * A Flight for cleanups of resource types that are not yet supported. Always results in failure
   * by failing to cleanup the resource.
   */
  public static class UnsupportedCleanupFlight extends Flight {
    public UnsupportedCleanupFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      JanitorDao janitorDao =
          ((ApplicationContext) applicationContext).getBean("janitorDao", JanitorDao.class);
      addStep(new InitialCleanupStep(janitorDao));
      addStep(new UnsupportedCleanupStep());
      addStep(new FinalCleanupStep(janitorDao));
    }
  }

  /** A Flight to cleanup a GoogleBucket. */
  public static class GoogleBucketCleanupFlight extends Flight {
    public GoogleBucketCleanupFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      JanitorDao janitorDao =
          ((ApplicationContext) applicationContext).getBean("janitorDao", JanitorDao.class);
      addStep(new InitialCleanupStep(janitorDao));
      addStep(new GoogleBucketCleanupStep());
      addStep(new FinalCleanupStep(janitorDao));
    }
  }
}
