package bio.terra.janitor.service.cleanup;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.GoogleBucketUid;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.PrimaryConfiguration;
import bio.terra.janitor.db.*;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class FlightSchedulerTest {

  @Autowired FlightScheduler flightScheduler;
  @Autowired PrimaryConfiguration primaryConfiguration;
  @Autowired JanitorDao janitorDao;

  @Test
  public void resourceScheduledForCleanup() throws Exception {
    Instant now = Instant.now();
    TrackedResource resource =
        TrackedResource.builder()
            .trackedResourceId(TrackedResourceId.create(UUID.randomUUID()))
            .trackedResourceState(TrackedResourceState.READY)
            .cloudResourceUid(
                new CloudResourceUid().googleBucketUid(new GoogleBucketUid().bucketName("foo")))
            .creation(now)
            .expiration(now)
            .build();
    janitorDao.createResource(resource, ImmutableMap.of());

    initializeScheduler();
    // TODO(wchamber): Finish flight lifecycle and check TrackedResourceState instead.
    Supplier<Boolean> flightIsFinished =
        () ->
            janitorDao.retrieveFlights(resource.trackedResourceId()).stream()
                .anyMatch(
                    cleanupFlight -> cleanupFlight.state().equals(CleanupFlightState.FINISHING));
    pollUntil(flightIsFinished, Duration.ofSeconds(1), 10);
  }

  private void initializeScheduler() {
    // Assumes that the scheduler is disabled by default.
    primaryConfiguration.setSchedulerEnabled(true);
    flightScheduler.initialize();
  }

  private void pollUntil(Supplier<Boolean> condition, Duration period, int maxNumPolls)
      throws InterruptedException {
    int numPolls = 0;
    while (numPolls < maxNumPolls) {
      TimeUnit.MILLISECONDS.sleep(period.toMillis());
      if (condition.get()) {
        return;
      }
      ++numPolls;
    }
    throw new InterruptedException("Polling exceeded maxNumPolls");
  }
}
