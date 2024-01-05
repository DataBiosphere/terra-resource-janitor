package bio.terra.janitor.service.cleanup;

import static bio.terra.janitor.service.cleanup.CleanupTestUtils.pollUntil;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

import bio.terra.janitor.app.configuration.PrimaryConfiguration;
import bio.terra.janitor.common.BaseUnitTest;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceKind;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.db.ResourceType;
import bio.terra.janitor.db.TrackedResource;
import bio.terra.janitor.db.TrackedResourceId;
import bio.terra.janitor.db.TrackedResourceState;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.janitor.generated.model.GoogleBucketUid;
import bio.terra.janitor.service.cleanup.flight.FatalStep;
import bio.terra.janitor.service.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class FlightSchedulerTest extends BaseUnitTest {

  // Construct a FlightScheduler manually instead of Autowired for ease of testing.
  private FlightScheduler flightScheduler;
  @Autowired JanitorDao janitorDao;
  @Autowired StairwayComponent stairwayComponent;
  @Autowired TransactionTemplate transactionTemplate;
  @MockBean private MetricsHelper mockMetricsHelper;

  private void initializeScheduler(FlightSubmissionFactory submissionFactory) {
    flightScheduler =
        new FlightScheduler(
            newPrimaryConfiguration(),
            stairwayComponent,
            janitorDao,
            transactionTemplate,
            submissionFactory,
            mockMetricsHelper);
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
        .metadata(ResourceMetadata.none())
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
    initializeScheduler(
        trackedResource ->
            FlightSubmissionFactory.FlightSubmission.create(FatalFlight.class, new FlightMap()));

    TrackedResource resource = newReadyExpiredResource(JanitorDao.currentInstant());
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

    TrackedResource resource = newReadyExpiredResource(JanitorDao.currentInstant());
    janitorDao.createResource(resource, ImmutableMap.of());

    pollUntil(
        () -> resourceStateIs(resource.trackedResourceId(), TrackedResourceState.ERROR),
        Duration.ofSeconds(1),
        10);
  }

  @Test
  public void recordResourceCount() {
    TrackedResource resource = newReadyExpiredResource(JanitorDao.currentInstant());
    janitorDao.createResource(resource, ImmutableMap.of());

    initializeScheduler(
        trackedResource ->
            FlightSubmissionFactory.FlightSubmission.create(FatalFlight.class, new FlightMap()));

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                verify(mockMetricsHelper)
                    .recordResourceKindGauge(
                        ResourceKind.create("", ResourceType.GOOGLE_BUCKET),
                        resource.trackedResourceState(),
                        1));
  }

  /** A {@link Flight} that ends fatally. */
  public static class FatalFlight extends Flight {
    public FatalFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new FatalStep());
    }
  }
}
