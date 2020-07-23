package bio.terra.janitor.service.cleanup;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.GoogleBucketUid;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.db.*;
import bio.terra.janitor.service.cleanup.flight.FinalCleanupStep;
import bio.terra.janitor.service.cleanup.flight.InitialCleanupStep;
import bio.terra.janitor.service.cleanup.flight.LatchStep;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class FlightManagerTest {
  private static final Instant CREATION = Instant.EPOCH;
  private static final Instant EXPIRATION = CREATION.plusSeconds(60);

  @Autowired StairwayComponent stairwayComponent;
  @Autowired JanitorDao janitorDao;

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

  /** Assert that the cleanup flight is finishing. */
  private void assertFlightFinishing(String flightId) {
    assertEquals(janitorDao.getFlightState(flightId), Optional.of(CleanupFlightState.FINISHING));
  }

  @Test
  public void scheduleFlight() throws Exception {
    FlightManager manager =
        new FlightManager(
            stairwayComponent.get(),
            janitorDao,
            trackedResource ->
                FlightFactory.FlightSubmission.create(OkCleanupFlight.class, new FlightMap()));
    TrackedResource resource = newResourceForCleaning();
    janitorDao.createResource(resource, ImmutableMap.of());

    Optional<String> flightId = manager.submitFlight(EXPIRATION);
    assertTrue(flightId.isPresent());
    blockUntilFlightComplete(flightId.get());

    assertFlightFinishing(flightId.get());
  }

  @Test
  public void scheduleFlight_nothingReady() {
    // No resources for cleaning inserted.
    FlightManager manager =
        new FlightManager(
            stairwayComponent.get(),
            janitorDao,
            trackedResource ->
                FlightFactory.FlightSubmission.create(OkCleanupFlight.class, new FlightMap()));
    assertFalse(manager.submitFlight(EXPIRATION).isPresent());
  }

  @Test
  public void recoverUnsubmittedFlights_unsubmittedFlight() throws Exception {
    FlightManager manager =
        new FlightManager(
            stairwayComponent.get(),
            janitorDao,
            trackedResource ->
                FlightFactory.FlightSubmission.create(OkCleanupFlight.class, new FlightMap()));
    TrackedResource resource = newResourceForCleaning();
    janitorDao.createResource(resource, ImmutableMap.of());

    // Create a flight outside of the manager to represent a stored flight that was not submitted.
    String flightId = stairwayComponent.get().createFlightId();
    janitorDao.updateResourceForCleaning(EXPIRATION, flightId);

    assertEquals(1, manager.recoverUnsubmittedFlights());

    blockUntilFlightComplete(flightId);

    assertFlightFinishing(flightId);
  }

  @Test
  public void recoverUnsubmittedFlights_submittedButStillInitializing() throws Exception {
    String latchKey = "foo";
    FlightMap inputMap = new FlightMap();
    LatchStep.createLatch(inputMap, latchKey);

    // Use the LatchBeforeCleanupFlight to ensure that the CleanupFlightState is not modified before
    // this test calls recoverUnsubmittedFlights.
    FlightManager manager =
        new FlightManager(
            stairwayComponent.get(),
            janitorDao,
            trackedResource ->
                FlightFactory.FlightSubmission.create(LatchBeforeCleanupFlight.class, inputMap));
    TrackedResource resource = newResourceForCleaning();
    janitorDao.createResource(resource, ImmutableMap.of());

    Optional<String> flightId = manager.submitFlight(EXPIRATION);
    assertTrue(flightId.isPresent());
    assertEquals(
        janitorDao.getFlightState(flightId.get()), Optional.of(CleanupFlightState.INITIATING));
    // The flight was submitted, so this should be a no-op.
    assertEquals(0, manager.recoverUnsubmittedFlights());

    // Let the flight finish now.
    LatchStep.releaseLatch(latchKey);
    blockUntilFlightComplete(flightId.get());

    assertFlightFinishing(flightId.get());
  }

  /** A basic cleanup {@link Flight} that uses the standard cleanup steps.. */
  public static class OkCleanupFlight extends Flight {
    public OkCleanupFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      JanitorDao janitorDao =
          ((ApplicationContext) applicationContext).getBean("janitorDao", JanitorDao.class);
      addStep(new InitialCleanupStep(janitorDao));
      addStep(new FinalCleanupStep(janitorDao));
    }
  }

  /** A {@link Flight} for cleanup that latches before setting the clenaup flight state. */
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
}
