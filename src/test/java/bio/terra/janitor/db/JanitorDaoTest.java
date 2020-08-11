package bio.terra.janitor.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.GoogleProjectUid;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.common.ResourceType;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class JanitorDaoTest {
  private static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value2");

  private static final Instant CREATION = Instant.now();
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
        .expiration(EXPIRATION);
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
  public void retrieveExpiredResourceWith() {
    TrackedResource resource =
        newDefaultResource()
            .trackedResourceState(TrackedResourceState.READY)
            .expiration(EXPIRATION)
            .build();
    janitorDao.createResource(resource, ImmutableMap.of());

    assertEquals(
        Optional.of(resource),
        janitorDao.retrieveExpiredResourceWith(EXPIRATION, TrackedResourceState.READY));
    assertEquals(
        Optional.empty(),
        janitorDao.retrieveExpiredResourceWith(EXPIRATION, TrackedResourceState.CLEANING));
    assertEquals(
        Optional.empty(),
        janitorDao.retrieveExpiredResourceWith(
            EXPIRATION.minusSeconds(1), TrackedResourceState.READY));
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
        janitorDao.retrieveResourcesWith(resourceUid2),
        Matchers.containsInAnyOrder(
            TrackedResourceAndLabels.create(otherUidResource, otherLabels)));
    assertThat(
        janitorDao.retrieveResourcesWith(resourceUid1),
        Matchers.containsInAnyOrder(
            TrackedResourceAndLabels.create(resource1, labels1),
            TrackedResourceAndLabels.create(resource2, labels2),
            TrackedResourceAndLabels.create(resource3, labels3)));

    assertThat(
        janitorDao.retrieveResourcesWith(
            new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId("project3"))),
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
    // No client label, but unrelated label.
    janitorDao.createResource(
        newDefaultResource().trackedResourceState(TrackedResourceState.CLEANING).build(),
        ImmutableMap.of("foo", "baz"));
    // No labels at all.
    janitorDao.createResource(
        newDefaultResource().trackedResourceState(TrackedResourceState.CLEANING).build(),
        ImmutableMap.of());

    assertThat(
        janitorDao.retrieveResourceCounts(),
        Matchers.containsInAnyOrder(
            ResourceStateCount.builder()
                .count(2)
                .trackedResourceState(TrackedResourceState.READY)
                .clientId("c1")
                .build(),
            ResourceStateCount.builder()
                .count(1)
                .trackedResourceState(TrackedResourceState.READY)
                .clientId("c2")
                .build(),
            ResourceStateCount.builder()
                .count(1)
                .trackedResourceState(TrackedResourceState.CLEANING)
                .clientId("c1")
                .build(),
            ResourceStateCount.builder()
                .count(2)
                .trackedResourceState(TrackedResourceState.CLEANING)
                .clientId("")
                .build()));
  }
}
