package bio.terra.janitor.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.common.BaseUnitTest;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.janitor.generated.model.GoogleBucketUid;
import bio.terra.janitor.generated.model.GoogleProjectUid;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class JanitorDaoTest extends BaseUnitTest {
  private static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value2");

  private static final Instant CREATION = JanitorDao.currentInstant();
  private static final Instant EXPIRATION = CREATION.plus(1, ChronoUnit.MINUTES);

  @Autowired JanitorJdbcConfiguration jdbcConfiguration;
  @Autowired JanitorDao janitorDao;

  private NamedParameterJdbcTemplate jdbcTemplate;

  @BeforeEach
  public void setup() {
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  private static TrackedResource.Builder newDefaultResource() {
    return TrackedResource.builder()
        .trackedResourceId(TrackedResourceId.create(UUID.randomUUID()))
        .trackedResourceState(TrackedResourceState.READY)
        .cloudResourceUid(
            new CloudResourceUid()
                .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString())))
        .creation(CREATION)
        .expiration(EXPIRATION)
        .metadata(ResourceMetadata.none());
  }

  @Test
  public void serializeCloudResourceUid() {
    CloudResourceUid cloudResourceUid =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId("my-project"));
    String serialized = "{\"googleProjectUid\":{\"projectId\":\"my-project\"}}";
    assertEquals(serialized, JanitorDao.serialize(cloudResourceUid));
    assertEquals(cloudResourceUid, JanitorDao.deserialize(serialized));
  }

  @Test
  public void deserializeMetadataV1() {
    String serialized = "{\"version\":1,\"googleProjectParent\":\"folders/1234\"}";
    ResourceMetadata metadata = JanitorDao.deserializeMetadata(serialized);
    assertTrue(metadata.googleProjectParent().isPresent());
    assertEquals("folders/1234", metadata.googleProjectParent().get());
  }

  @Test
  public void deserializeEmptyMetadataV1() {
    ResourceMetadata none = ResourceMetadata.none();
    String serializedV1 = "{\"version\":1}";
    assertEquals(none, JanitorDao.deserializeMetadata(serializedV1));
  }

  @Test
  public void serializeResourceMetadata() {
    ResourceMetadata metadata =
        ResourceMetadata.builder().googleProjectParent("folders/1234").build();
    String serialized = "{\"version\":2,\"googleProjectParent\":\"folders/1234\"}";
    assertEquals(serialized, JanitorDao.serialize(metadata));
    assertEquals(metadata, JanitorDao.deserializeMetadata(serialized));
  }

  @Test
  public void serializeResourceMetadataWorkspace() {
    ResourceMetadata metadata =
        ResourceMetadata.builder().workspaceOwner("fakeuser@test.firecloud.org").build();
    String serialized = "{\"version\":2,\"workspaceOwner\":\"fakeuser@test.firecloud.org\"}";
    assertEquals(serialized, JanitorDao.serialize(metadata));
    assertEquals(metadata, JanitorDao.deserializeMetadata(serialized));
  }

  @Test
  public void serializeResourceMetadataNone() {
    ResourceMetadata none = ResourceMetadata.none();
    String serialized = "{\"version\":2}";
    assertEquals(serialized, JanitorDao.serialize(none));
    assertEquals(none, JanitorDao.deserializeMetadata(serialized));
    assertEquals(none, JanitorDao.deserializeMetadata(null));
  }

  @Test
  public void createTrackedResource() {
    CloudResourceUid cloudResourceUid =
        new CloudResourceUid()
            .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
    TrackedResource resource =
        TrackedResource.builder()
            .trackedResourceId(TrackedResourceId.create(UUID.randomUUID()))
            .trackedResourceState(TrackedResourceState.READY)
            .cloudResourceUid(cloudResourceUid)
            .creation(CREATION)
            .expiration(EXPIRATION)
            .metadata(ResourceMetadata.builder().googleProjectParent("folders/1234").build())
            .build();
    janitorDao.createResource(resource, DEFAULT_LABELS);

    assertEquals(
        Optional.of(resource), janitorDao.retrieveTrackedResource(resource.trackedResourceId()));
    assertEquals(DEFAULT_LABELS, janitorDao.retrieveLabels(resource.trackedResourceId()));
    assertEquals(
        Optional.of(TrackedResourceAndLabels.create(resource, DEFAULT_LABELS)),
        janitorDao.retrieveResourceAndLabels(resource.trackedResourceId()));
    String resourceType =
        jdbcTemplate.queryForObject(
            "SELECT resource_type FROM tracked_resource WHERE id = :id",
            new MapSqlParameterSource().addValue("id", resource.trackedResourceId().uuid()),
            String.class);
    assertEquals(ResourceType.GOOGLE_PROJECT, ResourceType.valueOf(resourceType));
  }

  @Test
  public void retrieveTrackedResource_unknownId() {
    assertEquals(
        Optional.empty(),
        janitorDao.retrieveTrackedResource(TrackedResourceId.create(UUID.randomUUID())));
  }

  @Test
  public void retrieveLabels_noLabels() {
    TrackedResource resource = newDefaultResource().build();
    janitorDao.createResource(resource, ImmutableMap.of());
    assertThat(
        janitorDao.retrieveLabels(resource.trackedResourceId()).entrySet(), Matchers.empty());
  }

  @Test
  public void retrieveResourceAndLabels_noLabels() {
    TrackedResource resource = newDefaultResource().build();
    janitorDao.createResource(resource, ImmutableMap.of());
    assertEquals(
        Optional.of(TrackedResourceAndLabels.create(resource, ImmutableMap.of())),
        janitorDao.retrieveResourceAndLabels(resource.trackedResourceId()));
  }

  @Test
  public void retrieveResourceAndLabels_unknownId() {
    assertEquals(
        Optional.empty(),
        janitorDao.retrieveResourceAndLabels(TrackedResourceId.create(UUID.randomUUID())));
  }

  @Test
  public void updateResourceState() {
    TrackedResource resource =
        newDefaultResource().trackedResourceState(TrackedResourceState.READY).build();
    janitorDao.createResource(resource, ImmutableMap.of());

    TrackedResource expected =
        resource.toBuilder().trackedResourceState(TrackedResourceState.ABANDONED).build();
    assertEquals(
        Optional.of(expected),
        janitorDao.updateResourceState(
            resource.trackedResourceId(), TrackedResourceState.ABANDONED));
    assertEquals(
        Optional.of(expected), janitorDao.retrieveTrackedResource(resource.trackedResourceId()));
  }

  @Test
  public void updateResourceState_unknownId() {
    assertEquals(
        Optional.empty(),
        janitorDao.updateResourceState(
            TrackedResourceId.create(UUID.randomUUID()), TrackedResourceState.READY));
  }

  @Test
  public void retrieveResourcesMatching() {
    TrackedResource readyResource =
        newDefaultResource()
            .trackedResourceState(TrackedResourceState.READY)
            .expiration(EXPIRATION)
            .build();
    TrackedResource errorResource =
        newDefaultResource()
            .trackedResourceState(TrackedResourceState.ERROR)
            .expiration(EXPIRATION)
            .build();
    TrackedResource lateExpiredResource =
        newDefaultResource()
            .trackedResourceState(TrackedResourceState.ERROR)
            .expiration(EXPIRATION.plusSeconds(10))
            .build();
    janitorDao.createResource(readyResource, ImmutableMap.of());
    janitorDao.createResource(errorResource, ImmutableMap.of());
    janitorDao.createResource(lateExpiredResource, ImmutableMap.of());

    assertThat(
        janitorDao.retrieveResourcesMatching(
            TrackedResourceFilter.builder()
                .allowedStates(
                    ImmutableSet.of(TrackedResourceState.ABANDONED, TrackedResourceState.READY))
                .build()),
        Matchers.containsInAnyOrder(readyResource));
    assertThat(
        janitorDao.retrieveResourcesMatching(
            TrackedResourceFilter.builder()
                .forbiddenStates(ImmutableSet.of(TrackedResourceState.ERROR))
                .build()),
        Matchers.containsInAnyOrder(readyResource));
    assertThat(
        janitorDao.retrieveResourcesMatching(
            TrackedResourceFilter.builder()
                .cloudResourceUid(readyResource.cloudResourceUid())
                .build()),
        Matchers.containsInAnyOrder(readyResource));
    assertThat(
        janitorDao.retrieveResourcesMatching(
            TrackedResourceFilter.builder().expiredBy(EXPIRATION).build()),
        Matchers.containsInAnyOrder(readyResource, errorResource));
    assertThat(
        janitorDao.retrieveResourcesMatching(TrackedResourceFilter.builder().limit(1).build()),
        Matchers.hasSize(1));

    assertThat(
        janitorDao.retrieveResourcesMatching(TrackedResourceFilter.builder().build()),
        Matchers.containsInAnyOrder(readyResource, errorResource, lateExpiredResource));
    assertThat(
        janitorDao.retrieveResourcesMatching(
            TrackedResourceFilter.builder()
                .allowedStates(ImmutableSet.of(TrackedResourceState.CLEANING))
                .build()),
        Matchers.empty());
    assertThat(
        janitorDao.retrieveResourcesMatching(
            TrackedResourceFilter.builder()
                .allowedStates(ImmutableSet.of(TrackedResourceState.READY))
                .expiredBy(EXPIRATION)
                .limit(5)
                .build()),
        Matchers.containsInAnyOrder(readyResource));

    assertThat(
        janitorDao.retrieveResourcesMatching(
            TrackedResourceFilter.builder()
                .allowedStates(ImmutableSet.of(TrackedResourceState.ERROR))
                .limit(0)
                .build()),
        Matchers.containsInAnyOrder(errorResource, lateExpiredResource));

    List<TrackedResource> limit1 =
        janitorDao.retrieveResourcesMatching(
            TrackedResourceFilter.builder()
                .allowedStates(ImmutableSet.of(TrackedResourceState.ERROR))
                .limit(1)
                .build());
    assertThat(limit1, Matchers.hasSize(1));
    List<TrackedResource> limit1Offset1 =
        janitorDao.retrieveResourcesMatching(
            TrackedResourceFilter.builder()
                .allowedStates(ImmutableSet.of(TrackedResourceState.ERROR))
                .limit(1)
                .offset(1)
                .build());
    assertThat(limit1Offset1, Matchers.hasSize(1));
    assertNotEquals(limit1.get(0), limit1Offset1.get(0));
  }

  @Test
  public void cleanupFlight() {
    TrackedResource resource = newDefaultResource().build();
    janitorDao.createResource(resource, ImmutableMap.of());
    String flightId = "foo";
    janitorDao.createCleanupFlight(
        resource.trackedResourceId(),
        CleanupFlight.create(flightId, CleanupFlightState.INITIATING));

    CleanupFlight expectedFlight = CleanupFlight.create(flightId, CleanupFlightState.IN_FLIGHT);
    assertEquals(
        Optional.of(expectedFlight),
        janitorDao.updateFlightState(flightId, CleanupFlightState.IN_FLIGHT));

    assertThat(
        janitorDao.retrieveFlights(resource.trackedResourceId()),
        Matchers.contains(expectedFlight));
    assertEquals(
        janitorDao.retrieveFlightState(flightId), Optional.of(CleanupFlightState.IN_FLIGHT));
    assertThat(
        janitorDao.retrieveResourcesWith(CleanupFlightState.IN_FLIGHT, 10),
        Matchers.contains(TrackedResourceAndFlight.create(resource, expectedFlight)));
    assertThat(
        janitorDao.retrieveResourcesWith(CleanupFlightState.INITIATING, 10), Matchers.empty());
  }

  @Test
  public void getFlightState_unknownFlightId() {
    assertEquals(janitorDao.retrieveFlightState("unknown-flight-id"), Optional.empty());
  }

  @Test
  public void retrieveResourcesWithLabels() {
    CloudResourceUid resourceUid1 =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId("project1"));
    CloudResourceUid resourceUid2 =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId("project2"));

    TrackedResource resource1 = newDefaultResource().cloudResourceUid(resourceUid1).build();
    TrackedResource resource2 = newDefaultResource().cloudResourceUid(resourceUid1).build();
    TrackedResource resource3 = newDefaultResource().cloudResourceUid(resourceUid1).build();
    ImmutableMap<String, String> labels1 = ImmutableMap.of("a", "x", "b", "y");
    ImmutableMap<String, String> labels2 = ImmutableMap.of("a", "x");
    ImmutableMap<String, String> labels3 = ImmutableMap.of();
    janitorDao.createResource(resource1, labels1);
    janitorDao.createResource(resource2, labels2);
    janitorDao.createResource(resource3, labels3);
    TrackedResource otherUidResource = newDefaultResource().cloudResourceUid(resourceUid2).build();
    ImmutableMap<String, String> otherLabels = ImmutableMap.of("a", "b");
    janitorDao.createResource(otherUidResource, otherLabels);

    assertThat(
        janitorDao.retrieveResourcesAndLabels(
            TrackedResourceFilter.builder().cloudResourceUid(resourceUid2).build()),
        Matchers.containsInAnyOrder(
            TrackedResourceAndLabels.create(otherUidResource, otherLabels)));
    assertThat(
        janitorDao.retrieveResourcesAndLabels(
            TrackedResourceFilter.builder().cloudResourceUid(resourceUid1).build()),
        Matchers.containsInAnyOrder(
            TrackedResourceAndLabels.create(resource1, labels1),
            TrackedResourceAndLabels.create(resource2, labels2),
            TrackedResourceAndLabels.create(resource3, labels3)));

    assertThat(
        janitorDao.retrieveResourcesAndLabels(
            TrackedResourceFilter.builder()
                .cloudResourceUid(
                    new CloudResourceUid()
                        .googleProjectUid(new GoogleProjectUid().projectId("project3")))
                .build()),
        Matchers.empty());
  }

  @Test
  public void retrieveResourceAndFlight() {
    String flightId = "foo";
    TrackedResource resource = newDefaultResource().build();
    CleanupFlight cleanupFlight = CleanupFlight.create(flightId, CleanupFlightState.INITIATING);

    janitorDao.createResource(resource, ImmutableMap.of());
    janitorDao.createCleanupFlight(resource.trackedResourceId(), cleanupFlight);

    assertEquals(
        Optional.of(TrackedResourceAndFlight.create(resource, cleanupFlight)),
        janitorDao.retrieveResourceAndFlight(flightId));
  }

  @Test
  public void retrieveResourceAndFlight_unknownFlightId() {
    assertEquals(Optional.empty(), janitorDao.retrieveResourceAndFlight("unknown-flight-id"));
  }

  @Test
  public void retrieveResourceCounts() {
    janitorDao.createResource(
        newDefaultResource().trackedResourceState(TrackedResourceState.READY).build(),
        ImmutableMap.of("client", "c1", "foo", "bar"));
    janitorDao.createResource(
        newDefaultResource().trackedResourceState(TrackedResourceState.READY).build(),
        ImmutableMap.of("client", "c1"));
    janitorDao.createResource(
        newDefaultResource().trackedResourceState(TrackedResourceState.READY).build(),
        ImmutableMap.of("client", "c2"));
    janitorDao.createResource(
        newDefaultResource().trackedResourceState(TrackedResourceState.CLEANING).build(),
        ImmutableMap.of("client", "c1"));
    janitorDao.createResource(
        newDefaultResource()
            .trackedResourceState(TrackedResourceState.READY)
            .cloudResourceUid(
                new CloudResourceUid()
                    .googleBucketUid(new GoogleBucketUid().bucketName("my-bucket-name")))
            .build(),
        ImmutableMap.of("client", "c1"));
    // No client label, but unrelated label.
    janitorDao.createResource(
        newDefaultResource().trackedResourceState(TrackedResourceState.CLEANING).build(),
        ImmutableMap.of("foo", "baz"));
    // No labels at all.
    janitorDao.createResource(
        newDefaultResource().trackedResourceState(TrackedResourceState.CLEANING).build(),
        ImmutableMap.of());

    assertEquals(
        ImmutableTable.builder()
            .put(
                ResourceKind.create("c1", ResourceType.GOOGLE_PROJECT),
                TrackedResourceState.READY,
                2)
            .put(
                ResourceKind.create("c2", ResourceType.GOOGLE_PROJECT),
                TrackedResourceState.READY,
                1)
            .put(
                ResourceKind.create("c1", ResourceType.GOOGLE_BUCKET),
                TrackedResourceState.READY,
                1)
            .put(
                ResourceKind.create("c1", ResourceType.GOOGLE_PROJECT),
                TrackedResourceState.CLEANING,
                1)
            .put(
                ResourceKind.create("", ResourceType.GOOGLE_PROJECT),
                TrackedResourceState.CLEANING,
                2)
            .build(),
        janitorDao.retrieveResourceCounts());
  }
}
