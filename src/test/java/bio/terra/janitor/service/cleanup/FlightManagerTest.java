package bio.terra.janitor.service.cleanup;

import static bio.terra.janitor.service.cleanup.CleanupTestUtils.pollUntil;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.GoogleBucketUid;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.db.*;
import bio.terra.janitor.service.cleanup.flight.*;
import bio.terra.janitor.service.stairway.StairwayComponent;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.DatabaseOperationException;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("unit")
@ActiveProfiles("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class FlightManagerTest {
  private static final Instant CREATION = Instant.EPOCH;
  private static final Instant EXPIRATION = CREATION.plusSeconds(60);

  @Autowired StairwayComponent stairwayComponent;
  @Autowired JanitorDao janitorDao;
  @Autowired TransactionTemplate transactionTemplate;

  private FlightManager createFlightManager(FlightSubmissionFactory submissionFactory) {
    return new FlightManager(
        stairwayComponent.get(), janitorDao, transactionTemplate, submissionFactory);
  }

  private static TrackedResource newResourceForCleaning() {
    return TrackedResource.builder()
        .trackedResourceId(TrackedResourceId.create(UUID.randomUUID()))
        .trackedResourceState(TrackedResourceState.READY)
        .cloudResourceUid(
            new CloudResourceUid()
                .googleBucketUid(new GoogleBucketUid().bucketName(UUID.randomUUID().toString())))
        .creation(CREATION)
        .expiration(EXPIRATION)
        .build();
  }

  private void blockUntilFlightComplete(String flightId)
      throws InterruptedException, DatabaseOperationException {
    Duration maxWait = Duration.ofSeconds(10);
    Duration waited = Duration.ZERO;
    while (waited.compareTo(maxWait) < 0) {
      if (!stairwayComponent.get().getFlightState(flightId).isActive()) {
        return;
      }
      int pollMs = 100;
      waited.plus(Duration.ofMillis(pollMs));
      TimeUnit.MILLISECONDS.sleep(pollMs);
    }
    throw new InterruptedException("Flight did not complete in time.");
  }

  @Test
  public void scheduleAndCompleteFlight() throws Exception {
    FlightManager manager =
        createFlightManager(
            trackedResource ->
                FlightSubmissionFactory.FlightSubmission.create(
                    OkCleanupFlight.class, new FlightMap()));
    TrackedResource resource = newResourceForCleaning();
    janitorDao.createResource(resource, ImmutableMap.of());

    Optional<String> flightId = manager.submitFlight(EXPIRATION);
    assertTrue(flightId.isPresent());
    blockUntilFlightComplete(flightId.get());

    assertEquals(
        Optional.of(CleanupFlightState.FINISHING), janitorDao.retrieveFlightState(flightId.get()));
    assertEquals(1, manager.updateCompletedFlights(10));

    assertEquals(
        Optional.of(resource.toBuilder().trackedResourceState(TrackedResourceState.DONE).build()),
        janitorDao.retrieveTrackedResource(resource.trackedResourceId()));
    assertEquals(
        Optional.of(CleanupFlightState.FINISHED), janitorDao.retrieveFlightState(flightId.get()));

    // No more work to be done once the flight is completed.
    assertFalse(manager.submitFlight(EXPIRATION).isPresent());
    assertEquals(0, manager.updateCompletedFlights(10));
  }

  @Test
  public void scheduleFlight_nothingReady() {
    // No resources for cleaning inserted.
    FlightManager manager =
        createFlightManager(
            trackedResource ->
                FlightSubmissionFactory.FlightSubmission.create(
                    OkCleanupFlight.class, new FlightMap()));
    assertFalse(manager.submitFlight(EXPIRATION).isPresent());
  }

  @Test
  public void recoverUnsubmittedFlights_unsubmittedFlight() throws Exception {
    FlightManager manager =
        createFlightManager(
            trackedResource ->
                FlightSubmissionFactory.FlightSubmission.create(
                    OkCleanupFlight.class, new FlightMap()));
    // Create a resource with a flight outside of the manager to represent a stored flight that was
    // not submitted.
    TrackedResource resource =
        newResourceForCleaning()
            .toBuilder()
            .trackedResourceState(TrackedResourceState.CLEANING)
            .build();
    janitorDao.createResource(resource, ImmutableMap.of());
    String flightId = stairwayComponent.get().createFlightId();
    janitorDao.createCleanupFlight(
        resource.trackedResourceId(),
        CleanupFlight.create(flightId, CleanupFlightState.INITIATING));

    assertEquals(1, manager.recoverUnsubmittedFlights(10));

    blockUntilFlightComplete(flightId);

    assertEquals(
        Optional.of(CleanupFlightState.FINISHING), janitorDao.retrieveFlightState(flightId));
  }

  @Test
  public void recoverUnsubmittedFlights_submittedButStillInitializing() throws Exception {
    String latchKey = "foo";
    FlightMap inputMap = new FlightMap();
    LatchStep.createLatch(inputMap, latchKey);

    // Use the LatchBeforeCleanupFlight to ensure that the CleanupFlightState is not modified before
    // this test calls recoverUnsubmittedFlights.
    FlightManager manager =
        createFlightManager(
            trackedResource ->
                FlightSubmissionFactory.FlightSubmission.create(
                    LatchBeforeCleanupFlight.class, inputMap));
    TrackedResource resource = newResourceForCleaning();
    janitorDao.createResource(resource, ImmutableMap.of());

    Optional<String> flightId = manager.submitFlight(EXPIRATION);
    assertTrue(flightId.isPresent());
    assertEquals(
        janitorDao.retrieveFlightState(flightId.get()), Optional.of(CleanupFlightState.INITIATING));
    // The flight was submitted, so this should be a no-op.
    assertEquals(0, manager.recoverUnsubmittedFlights(10));

    // Let the flight finish now.
    LatchStep.releaseLatch(latchKey);
    blockUntilFlightComplete(flightId.get());

    assertEquals(
        Optional.of(CleanupFlightState.FINISHING), janitorDao.retrieveFlightState(flightId.get()));
  }

  @Test
  public void updateCompletedFlights_nothingComplete() {
    FlightManager manager =
        createFlightManager(
            trackedResource ->
                FlightSubmissionFactory.FlightSubmission.create(
                    OkCleanupFlight.class, new FlightMap()));
    assertEquals(0, manager.updateCompletedFlights(10));
  }

  @Test
  public void updateCompletedFlights_errorFlight() throws Exception {
    FlightManager manager =
        createFlightManager(
            trackedResource ->
                FlightSubmissionFactory.FlightSubmission.create(
                    ErrorCleanupFlight.class, new FlightMap()));
    TrackedResource resource = newResourceForCleaning();
    janitorDao.createResource(resource, ImmutableMap.of());

    Optional<String> flightId = manager.submitFlight(EXPIRATION);
    blockUntilFlightComplete(flightId.get());
    assertEquals(1, manager.updateCompletedFlights(10));

    assertEquals(
        Optional.of(resource.toBuilder().trackedResourceState(TrackedResourceState.ERROR).build()),
        janitorDao.retrieveTrackedResource(resource.trackedResourceId()));
    assertEquals(
        Optional.of(CleanupFlightState.FINISHED), janitorDao.retrieveFlightState(flightId.get()));
  }

  @Test
  public void updateCompletedFlights_waitsUntilFlightFinished() throws Exception {
    String latchKey = "foo";
    FlightMap inputMap = new FlightMap();
    LatchStep.createLatch(inputMap, latchKey);

    FlightManager manager =
        createFlightManager(
            trackedResource ->
                FlightSubmissionFactory.FlightSubmission.create(
                    LatchAfterCleanupFlight.class, inputMap));
    TrackedResource resource = newResourceForCleaning();
    janitorDao.createResource(resource, ImmutableMap.of());

    Optional<String> flightId = manager.submitFlight(EXPIRATION);
    // Test that the manager does not update flights that Stairway hasn't finished with even if the
    // CleanupFlightState is finishing.
    pollUntil(
        () ->
            janitorDao
                .retrieveFlightState(flightId.get())
                .equals(Optional.of(CleanupFlightState.FINISHING)),
        Duration.ofMillis(100),
        10);
    assertEquals(0, manager.updateCompletedFlights(10));

    LatchStep.releaseLatch(latchKey);
    blockUntilFlightComplete(flightId.get());
    assertEquals(1, manager.updateCompletedFlights(10));

    assertEquals(
        Optional.of(resource.toBuilder().trackedResourceState(TrackedResourceState.DONE).build()),
        janitorDao.retrieveTrackedResource(resource.trackedResourceId()));
    assertEquals(
        Optional.of(CleanupFlightState.FINISHED), janitorDao.retrieveFlightState(flightId.get()));
  }

  @Test
  public void updateCompletedState_stateModifiedDuringCleaning() throws Exception {
    String latchKey = "foo";
    FlightMap inputMap = new FlightMap();
    LatchStep.createLatch(inputMap, latchKey);

    FlightManager manager =
        createFlightManager(
            trackedResource ->
                FlightSubmissionFactory.FlightSubmission.create(
                    LatchAfterCleanupFlight.class, inputMap));

    TrackedResource duplicatedResource = newResourceForCleaning();
    janitorDao.createResource(duplicatedResource, ImmutableMap.of());
    String duplicatedFlight = manager.submitFlight(EXPIRATION).get();

    TrackedResource abandonedResource = newResourceForCleaning();
    janitorDao.createResource(abandonedResource, ImmutableMap.of());
    String abandonedFlight = manager.submitFlight(EXPIRATION).get();

    TrackedResource readyResource = newResourceForCleaning();
    janitorDao.createResource(readyResource, ImmutableMap.of());
    String readyFlight = manager.submitFlight(EXPIRATION).get();

    // The resource is modified while the flight is being cleaned up.
    janitorDao.updateResourceState(
        duplicatedResource.trackedResourceId(), TrackedResourceState.DUPLICATED);
    janitorDao.updateResourceState(
        abandonedResource.trackedResourceId(), TrackedResourceState.ABANDONED);
    janitorDao.updateResourceState(readyResource.trackedResourceId(), TrackedResourceState.READY);

    LatchStep.releaseLatch(latchKey);
    blockUntilFlightComplete(duplicatedFlight);
    blockUntilFlightComplete(abandonedFlight);
    blockUntilFlightComplete(readyFlight);
    // Only the duplicated and abandoned tracked resource states (2) are allowed to complete.
    assertEquals(2, manager.updateCompletedFlights(10));

    assertEquals(
        Optional.of(
            duplicatedResource
                .toBuilder()
                .trackedResourceState(TrackedResourceState.DUPLICATED)
                .build()),
        janitorDao.retrieveTrackedResource(duplicatedResource.trackedResourceId()));
    assertEquals(
        Optional.of(CleanupFlightState.FINISHED), janitorDao.retrieveFlightState(duplicatedFlight));

    assertEquals(
        Optional.of(
            abandonedResource
                .toBuilder()
                .trackedResourceState(TrackedResourceState.ABANDONED)
                .build()),
        janitorDao.retrieveTrackedResource(abandonedResource.trackedResourceId()));
    assertEquals(
        Optional.of(CleanupFlightState.FINISHED), janitorDao.retrieveFlightState(abandonedFlight));

    assertEquals(
        Optional.of(
            readyResource.toBuilder().trackedResourceState(TrackedResourceState.READY).build()),
        janitorDao.retrieveTrackedResource(readyResource.trackedResourceId()));
    assertEquals(
        Optional.of(CleanupFlightState.FINISHING), janitorDao.retrieveFlightState(readyFlight));
  }

  @Test
  public void updateFatalFlight() throws Exception {
    FlightManager manager =
        createFlightManager(
            trackedResource ->
                FlightSubmissionFactory.FlightSubmission.create(
                    FatalFlight.class, new FlightMap()));
    TrackedResource resource = newResourceForCleaning();
    janitorDao.createResource(resource, ImmutableMap.of());
    Optional<String> flightId = manager.submitFlight(EXPIRATION);
    blockUntilFlightComplete(flightId.get());

    // Updates for completed flights does not include fatal flights.
    assertEquals(0, manager.updateCompletedFlights(10));

    assertEquals(1, manager.updateFatalFlights(10));
    assertEquals(
        Optional.of(CleanupFlightState.FATAL), janitorDao.retrieveFlightState(flightId.get()));
    assertEquals(
        Optional.of(resource.toBuilder().trackedResourceState(TrackedResourceState.ERROR).build()),
        janitorDao.retrieveTrackedResource(resource.trackedResourceId()));
  }

  @Test
  public void updateFatalFlight_stateModifiedDuringCleaning() throws Exception {
    String latchKey = "foo";
    FlightMap inputMap = new FlightMap();
    LatchStep.createLatch(inputMap, latchKey);

    FlightManager manager =
        createFlightManager(
            trackedResource ->
                FlightSubmissionFactory.FlightSubmission.create(
                    LatchBeforeFatalFlight.class, inputMap));

    TrackedResource duplicatedResource = newResourceForCleaning();
    janitorDao.createResource(duplicatedResource, ImmutableMap.of());
    String duplicatedFlight = manager.submitFlight(EXPIRATION).get();

    TrackedResource abandonedResource = newResourceForCleaning();
    janitorDao.createResource(abandonedResource, ImmutableMap.of());
    String abandonedFlight = manager.submitFlight(EXPIRATION).get();

    TrackedResource readyResource = newResourceForCleaning();
    janitorDao.createResource(readyResource, ImmutableMap.of());
    String readyFlight = manager.submitFlight(EXPIRATION).get();

    // The resource is modified while the flight is being cleaned up.
    janitorDao.updateResourceState(
        duplicatedResource.trackedResourceId(), TrackedResourceState.DUPLICATED);
    janitorDao.updateResourceState(
        abandonedResource.trackedResourceId(), TrackedResourceState.ABANDONED);
    janitorDao.updateResourceState(readyResource.trackedResourceId(), TrackedResourceState.READY);

    LatchStep.releaseLatch(latchKey);
    blockUntilFlightComplete(duplicatedFlight);
    blockUntilFlightComplete(abandonedFlight);
    blockUntilFlightComplete(readyFlight);
    // Only the duplicated and abandoned tracked resource states (2) are updated for fatal.
    assertEquals(2, manager.updateFatalFlights(10));

    assertEquals(
        Optional.of(
            duplicatedResource
                .toBuilder()
                .trackedResourceState(TrackedResourceState.DUPLICATED)
                .build()),
        janitorDao.retrieveTrackedResource(duplicatedResource.trackedResourceId()));
    assertEquals(
        Optional.of(CleanupFlightState.FATAL), janitorDao.retrieveFlightState(duplicatedFlight));

    assertEquals(
        Optional.of(
            abandonedResource
                .toBuilder()
                .trackedResourceState(TrackedResourceState.ABANDONED)
                .build()),
        janitorDao.retrieveTrackedResource(abandonedResource.trackedResourceId()));
    assertEquals(
        Optional.of(CleanupFlightState.FATAL), janitorDao.retrieveFlightState(abandonedFlight));

    assertEquals(
        Optional.of(
            readyResource.toBuilder().trackedResourceState(TrackedResourceState.READY).build()),
        janitorDao.retrieveTrackedResource(readyResource.trackedResourceId()));
    assertEquals(
        Optional.of(CleanupFlightState.INITIATING), janitorDao.retrieveFlightState(readyFlight));
  }

  /** A basic cleanup {@link Flight} that uses the standard cleanup steps. */
  public static class OkCleanupFlight extends Flight {
    public OkCleanupFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      JanitorDao janitorDao =
          ((ApplicationContext) applicationContext).getBean("janitorDao", JanitorDao.class);
      addStep(new InitialCleanupStep(janitorDao));
      addStep(new FinalCleanupStep(janitorDao));
    }
  }

  /** A basic cleanup {@link Flight} that ends in error. */
  public static class ErrorCleanupFlight extends Flight {
    public ErrorCleanupFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      JanitorDao janitorDao =
          ((ApplicationContext) applicationContext).getBean("janitorDao", JanitorDao.class);
      addStep(new InitialCleanupStep(janitorDao));
      addStep(new UnsupportedCleanupStep());
      addStep(new FinalCleanupStep(janitorDao));
    }
  }

  /** A {@link Flight} that ends in a dismal fatal failure. */
  public static class FatalFlight extends Flight {
    public FatalFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new FatalStep());
    }
  }

  /** A {@link Flight} for cleanup that latches before setting the cleanup flight state. */
  public static class LatchBeforeCleanupFlight extends Flight {
    public LatchBeforeCleanupFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      JanitorDao janitorDao =
          ((ApplicationContext) applicationContext).getBean("janitorDao", JanitorDao.class);
      addStep(new LatchStep());
      addStep(new InitialCleanupStep(janitorDao));
      addStep(new FinalCleanupStep(janitorDao));
    }
  }

  /** A {@link Flight} for cleanup that latches after setting the finishing flight state. */
  public static class LatchAfterCleanupFlight extends Flight {
    public LatchAfterCleanupFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      JanitorDao janitorDao =
          ((ApplicationContext) applicationContext).getBean("janitorDao", JanitorDao.class);
      addStep(new InitialCleanupStep(janitorDao));
      addStep(new FinalCleanupStep(janitorDao));
      addStep(new LatchStep());
    }
  }

  /** A {@link Flight} that latches before ending in a dismal fatal failure. */
  public static class LatchBeforeFatalFlight extends Flight {
    public LatchBeforeFatalFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new LatchStep());
      addStep(new FatalStep());
    }
  }
}
