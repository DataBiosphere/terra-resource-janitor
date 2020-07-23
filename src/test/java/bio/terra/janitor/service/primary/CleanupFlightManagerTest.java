package bio.terra.janitor.service.primary;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.GoogleBucketUid;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.db.*;
import bio.terra.janitor.service.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.exception.DatabaseOperationException;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
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

  @Test
  public void scheduleFlight() throws Exception {
    TrackedResource resource = newResourceForCleaning();
    janitorDao.createResource(resource, ImmutableMap.of());
    CleanupFlightManager manager =
        new CleanupFlightManager(
            stairwayComponent.get(),
            janitorDao,
            trackedResource ->
                CleanupFlightFactory.FlightSubmission.create(
                    OkCleanupFlight.class, new FlightMap()));

    Optional<String> flightId = manager.submitFlight();
    assertTrue(flightId.isPresent());
    blockUntilFlightComplete(flightId.get());

    List<CleanupFlight> flights = janitorDao.getFlights(resource.trackedResourceId());
    assertThat(
        flights,
        Matchers.contains(CleanupFlight.create(flightId.get(), CleanupFlightState.FINISHING)));
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
    assertTrue(manager.submitFlight().isEmpty());
  }

  /** A basic cleanup flight that */
  public static class OkCleanupFlight extends Flight {
    public OkCleanupFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      JanitorDao janitorDao =
          ((ApplicationContext) applicationContext).getBean("janitorDao", JanitorDao.class);
      addStep(new InitialCleanupStep(janitorDao));
      addStep(new FinalCleanupStep(janitorDao));
    }
  }
}
