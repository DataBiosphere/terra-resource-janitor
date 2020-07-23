package bio.terra.janitor.service.primary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.GoogleBucketUid;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.db.*;
import bio.terra.janitor.service.stairway.StairwayComponent;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.DatabaseOperationException;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
public class CleanupFlightManagerTest {
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
    CleanupFlightManager manager =
        new CleanupFlightManager(
            stairwayComponent.get(),
            janitorDao,
            trackedResource ->
                CleanupFlightFactory.FlightSubmission.create(
                    OkCleanupFlight.class, new FlightMap()));
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
    CleanupFlightManager manager =
        new CleanupFlightManager(
            stairwayComponent.get(),
            janitorDao,
            trackedResource ->
                CleanupFlightFactory.FlightSubmission.create(
                    OkCleanupFlight.class, new FlightMap()));
    assertTrue(manager.submitFlight(EXPIRATION).isEmpty());
  }

  @Test
  public void recoverUnsubmittedFlights_unsubmittedFlight() throws Exception {
    CleanupFlightManager manager =
        new CleanupFlightManager(
            stairwayComponent.get(),
            janitorDao,
            trackedResource ->
                CleanupFlightFactory.FlightSubmission.create(
                    OkCleanupFlight.class, new FlightMap()));
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

    CleanupFlightManager manager =
        new CleanupFlightManager(
            stairwayComponent.get(),
            janitorDao,
            trackedResource ->
                CleanupFlightFactory.FlightSubmission.create(LatchBeforeCleanup.class, inputMap));
    TrackedResource resource = newResourceForCleaning();
    janitorDao.createResource(resource, ImmutableMap.of());

    Optional<String> flightId = manager.submitFlight(EXPIRATION);
    assertTrue(flightId.isPresent());
    // The flight was submitted, so this should be a no-op.
    assertEquals(0, manager.recoverUnsubmittedFlights());

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
  public static class LatchBeforeCleanup extends Flight {

    public LatchBeforeCleanup(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      JanitorDao janitorDao =
          ((ApplicationContext) applicationContext).getBean("janitorDao", JanitorDao.class);
      addStep(new LatchStep());
      addStep(new InitialCleanupStep(janitorDao));
      addStep(new FinalCleanupStep(janitorDao));
    }
  }

  /**
   * A step to block until the latch is released to be used for testing.
   *
   * <p>This step relies on in-memory state and does not work across services or after a service
   * restart.
   */
  public static class LatchStep implements Step {
    private static ConcurrentHashMap<String, CountDownLatch> latches = new ConcurrentHashMap<>();

    private static final String FLIGHT_MAP_KEY = "LatchStep";

    /**
     * Initialize a new latch at {@code key} and add a parameter for it to the {@link FlightMap}.
     * Replaces any existing latches with the same key.
     */
    public static void createLatch(FlightMap flightMap, String latchKey) {
      latches.put(latchKey, new CountDownLatch(1));
      flightMap.put(FLIGHT_MAP_KEY, latchKey);
    }

    /** Releases a latch added with {@link #createLatch(FlightMap, String)}. */
    public static void releaseLatch(String latchKey) {
      latches.get(latchKey).countDown();
    }

    @Override
    public StepResult doStep(FlightContext flightContext) throws InterruptedException {
      String latchKey = flightContext.getInputParameters().get(FLIGHT_MAP_KEY, String.class);
      CountDownLatch latch = latches.get(latchKey);
      Preconditions.checkNotNull(latch, "Expected a latch to exist with key %s", latchKey);
      latch.await();
      return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext flightContext) {
      return StepResult.getStepResultSuccess();
    }
  }
}
