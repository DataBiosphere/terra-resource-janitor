package bio.terra.janitor.service.cleanup;

import static bio.terra.janitor.service.cleanup.CleanupTestUtils.pollUntil;
import static bio.terra.janitor.service.cleanup.CleanupTestUtils.sleepForMetricsExport;
import static org.hamcrest.MatcherAssert.assertThat;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.GoogleBucketUid;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.PrimaryConfiguration;
import bio.terra.janitor.db.*;
import bio.terra.janitor.service.cleanup.flight.FatalStep;
import bio.terra.janitor.service.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class FlightSchedulerTest {

  // Construct a FlightScheduler manually instead of Autowired for ease of testing.
  private FlightScheduler flightScheduler;
  @Autowired JanitorDao janitorDao;
  @Autowired StairwayComponent stairwayComponent;
  @Autowired TransactionTemplate transactionTemplate;

  private void initializeScheduler(FlightSubmissionFactory submissionFactory) {
    flightScheduler =
            new FlightScheduler(
                    newPrimaryConfiguration(),
                    stairwayComponent,
                    janitorDao,
                    transactionTemplate,
                    submissionFactory);
    flightScheduler.initialize();
  }

  @AfterEach
  public void tearDown() {
    // Shutdown the FlightScheduler so that it isn't running during other tests.
    if (flightScheduler != null) {
      flightScheduler.shutdown();
    }
  }

  private PrimaryConfiguration newPrimaryConfiguration() {
    PrimaryConfiguration primaryConfiguration = new PrimaryConfiguration();
    primaryConfiguration.setSchedulerEnabled(true);
    primaryConfiguration.setFlightCompletionPeriod(Duration.ofSeconds(2));
    primaryConfiguration.setFlightSubmissionPeriod(Duration.ofSeconds(2));
    primaryConfiguration.setFatalFlightCompletionPeriod(Duration.ofSeconds(2));
    primaryConfiguration.setRecordResourceCountPeriod(Duration.ofSeconds(2));
    return primaryConfiguration;
  }

  /** Returns a new {@link TrackedResource} that is ready for cleanup {@code expiredBy}. */
  private TrackedResource newReadyExpiredResource(Instant expiredBy) {
    return TrackedResource.builder()
            .trackedResourceId(TrackedResourceId.create(UUID.randomUUID()))
            .trackedResourceState(TrackedResourceState.READY)
            .cloudResourceUid(
                    new CloudResourceUid().googleBucketUid(new GoogleBucketUid().bucketName("foo")))
            .creation(expiredBy)
            .expiration(expiredBy)
            .build();
  }

  private boolean resourceStateIs(TrackedResourceId id, TrackedResourceState expectedState) {
    return janitorDao
            .retrieveTrackedResource(id)
            .get()
            .trackedResourceState()
            .equals(expectedState);
  }

  @Test
  public void resourceScheduledForCleanup() throws Exception {
    initializeScheduler(new FlightSubmissionFactoryImpl());

    TrackedResource resource = newReadyExpiredResource(Instant.now());
    janitorDao.createResource(resource, ImmutableMap.of());

    pollUntil(
            () -> resourceStateIs(resource.trackedResourceId(), TrackedResourceState.ERROR),
            Duration.ofSeconds(1),
            10);
  }

  @Test
  public void fatalCleanupCompleted() throws Exception {
    FlightSubmissionFactory fatalFactory =
            trackedResource ->
                    FlightSubmissionFactory.FlightSubmission.create(FatalFlight.class, new FlightMap());
    initializeScheduler(fatalFactory);

    TrackedResource resource = newReadyExpiredResource(Instant.now());
    janitorDao.createResource(resource, ImmutableMap.of());

    pollUntil(
            () -> resourceStateIs(resource.trackedResourceId(), TrackedResourceState.ERROR),
            Duration.ofSeconds(1),
            10);
  }

  @Test
  public void recordResourceCount() throws Exception {
    TrackedResource resource = newReadyExpiredResource(Instant.now());
    janitorDao.createResource(resource, ImmutableMap.of());

    initializeScheduler(
            trackedResource ->
                    FlightSubmissionFactory.FlightSubmission.create(FatalFlight.class, new FlightMap()));

    sleepForMetricsExport();

    assertThat(
            MetricsHelper.VIEW_MANAGER
                    .getView(MetricsHelper.TRACKED_RESOURCE_COUNT_VIEW.getName())
                    .getAggregationMap()
                    .size(),
            Matchers.greaterThan(0));
  }

  /** A {@link Flight} that ends fatally. */
  public static class FatalFlight extends Flight {
    public FatalFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new FatalStep());
    }
  }
}