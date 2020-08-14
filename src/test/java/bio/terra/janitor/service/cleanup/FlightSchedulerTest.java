package bio.terra.janitor.service.cleanup;

import static bio.terra.janitor.service.cleanup.CleanupTestUtils.pollUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.GoogleBucketUid;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.PrimaryConfiguration;
import bio.terra.janitor.common.configuration.TestConfiguration;
import bio.terra.janitor.db.*;
import bio.terra.janitor.service.cleanup.flight.FatalStep;
import bio.terra.janitor.service.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
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

  // Construct a FlightScheduler manually instead of Autowired for ease of testing.
  private FlightScheduler flightScheduler;
  @Autowired JanitorDao janitorDao;
  @Autowired StairwayComponent stairwayComponent;
  @Autowired TestConfiguration testConfiguration;

  private StorageCow storageCow;

  @BeforeEach
  public void setUp() throws Exception {
    storageCow =
        new StorageCow(
            testConfiguration.getCrlClientConfig(),
            StorageOptions.newBuilder()
                .setCredentials(testConfiguration.getResourceAccessGoogleCredentialsOrDie())
                .setProjectId(testConfiguration.getResourceProjectId())
                .build());
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
    return primaryConfiguration;
  }

  /** Returns a new {@link TrackedResource} that is ready for cleanup {@code expiredBy}. */
  private TrackedResource newReadyExpiredResource(CloudResourceUid resource, Instant expiredBy) {
    return TrackedResource.builder()
        .trackedResourceId(TrackedResourceId.create(UUID.randomUUID()))
        .trackedResourceState(TrackedResourceState.READY)
        .cloudResourceUid(resource)
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
  public void resourceScheduledForCleanup_googleBucket() throws Exception {
    // Creates bucket and verify.
    String bucketName = UUID.randomUUID().toString();
    assertNull(storageCow.get(bucketName));
    BucketCow createdBucket = storageCow.create(BucketInfo.of(bucketName));
    assertEquals(bucketName, createdBucket.getBucketInfo().getName());
    assertEquals(bucketName, storageCow.get(bucketName).getBucketInfo().getName());

    TrackedResource resource =
        newReadyExpiredResource(
            new CloudResourceUid().googleBucketUid(new GoogleBucketUid().bucketName(bucketName)),
            Instant.now());

    flightScheduler =
        new FlightScheduler(
            newPrimaryConfiguration(),
            stairwayComponent,
            janitorDao,
            new FlightSubmissionFactoryImpl());
    flightScheduler.initialize();

    janitorDao.createResource(resource, ImmutableMap.of());

    pollUntil(
        () -> resourceStateIs(resource.trackedResourceId(), TrackedResourceState.DONE),
        Duration.ofSeconds(1),
        10);

    // Resource is removed
    assertNull(storageCow.get(bucketName));
  }

  @Test
  public void fatalCleanupCompleted() throws Exception {
    FlightSubmissionFactory fatalFactory =
        trackedResource ->
            FlightSubmissionFactory.FlightSubmission.create(FatalFlight.class, new FlightMap());
    flightScheduler =
        new FlightScheduler(newPrimaryConfiguration(), stairwayComponent, janitorDao, fatalFactory);
    flightScheduler.initialize();

    TrackedResource resource =
        newReadyExpiredResource(
            new CloudResourceUid().googleBucketUid(new GoogleBucketUid().bucketName("foo")),
            Instant.now());
    janitorDao.createResource(resource, ImmutableMap.of());

    pollUntil(
        () -> resourceStateIs(resource.trackedResourceId(), TrackedResourceState.ERROR),
        Duration.ofSeconds(1),
        10);
  }

  /** A {@link Flight} that ends fatally. */
  public static class FatalFlight extends Flight {
    public FatalFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new FatalStep());
    }
  }
}
