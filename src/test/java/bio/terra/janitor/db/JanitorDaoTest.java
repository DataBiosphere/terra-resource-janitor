package bio.terra.janitor.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.GoogleProjectUid;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.common.ResourceType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.sql.Timestamp;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
public class JanitorDaoTest {
  private static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value2");

  private static final Instant CREATION = Instant.now();
  private static final Instant EXPIRATION = CREATION.plus(1, ChronoUnit.MINUTES);

  @Autowired JanitorJdbcConfiguration jdbcConfiguration;
  @Autowired JanitorDao janitorDao;
  @Autowired DatabaseTestUtils databaseTestUtils;

  private NamedParameterJdbcTemplate jdbcTemplate;

  @BeforeEach
  public void setup() {
    databaseTestUtils.resetJanitorDb();
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  public static TrackedResource.Builder newDefaultResource() {
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
  public void createTrackedResource() throws Exception {
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

    assertCreateResultMatch(
        resource.trackedResourceId(),
        cloudResourceUid,
        ResourceType.GOOGLE_PROJECT,
        CREATION,
        EXPIRATION,
        DEFAULT_LABELS);
  }

  @Test
  public void updateResourceForCleaning_OnlyReadyExpired() {
    // This resource is ready to update to cleaning once it has expired.
    TrackedResource readyResource =
        newDefaultResource()
            .trackedResourceState(TrackedResourceState.READY)
            .expiration(EXPIRATION)
            .build();
    // This resource is already cleaning and should not be updated again.
    TrackedResource expiredCleaningResource =
        newDefaultResource()
            .trackedResourceState(TrackedResourceState.CLEANING)
            .expiration(Instant.EPOCH)
            .build();
    janitorDao.createResource(readyResource, ImmutableMap.of());
    janitorDao.createResource(expiredCleaningResource, ImmutableMap.of());

    String flightId = "foo";
    assertEquals(
        Optional.empty(),
        janitorDao.updateResourceForCleaning(EXPIRATION.minusSeconds(1), flightId));
    Optional<TrackedResource> updated = janitorDao.updateResourceForCleaning(EXPIRATION, flightId);

    TrackedResource expected =
        readyResource.toBuilder().trackedResourceState(TrackedResourceState.CLEANING).build();
    assertTrue(updated.isPresent());
    assertEquals(expected, updated.get());

    List<JanitorDao.TrackedResourceAndFlight> resourceAndFlights =
        janitorDao.retrieveResourcesWith(CleanupFlightState.INITIATING, 10);
    assertThat(
        resourceAndFlights,
        Matchers.contains(
            JanitorDao.TrackedResourceAndFlight.create(
                expected, CleanupFlight.create(flightId, CleanupFlightState.INITIATING))));
  }

  @Test
  public void flightState() {
    TrackedResource resource =
        newDefaultResource()
            .trackedResourceState(TrackedResourceState.READY)
            .expiration(EXPIRATION)
            .build();
    janitorDao.createResource(resource, ImmutableMap.of());
    String flightId = "foo";
    TrackedResource expected =
        resource.toBuilder().trackedResourceState(TrackedResourceState.CLEANING).build();
    assertEquals(janitorDao.updateResourceForCleaning(EXPIRATION, flightId).get(), expected);

    janitorDao.setFlightState(flightId, CleanupFlightState.IN_FLIGHT);

    CleanupFlight expectedFlight = CleanupFlight.create(flightId, CleanupFlightState.IN_FLIGHT);
    assertThat(
        janitorDao.getFlights(resource.trackedResourceId()), Matchers.contains(expectedFlight));
    assertThat(
        janitorDao.retrieveResourcesWith(CleanupFlightState.IN_FLIGHT, 10),
        Matchers.contains(JanitorDao.TrackedResourceAndFlight.create(expected, expectedFlight)));
    assertThat(
        janitorDao.retrieveResourcesWith(CleanupFlightState.INITIATING, 10), Matchers.empty());
  }

  public static Map<String, Object> queryTrackedResource(
      NamedParameterJdbcTemplate jdbcTemplate, CloudResourceUid resourceUid) {
    String sql =
        "SELECT id, resource_uid::text, resource_type, creation, expiration, state "
            + "FROM tracked_resource "
            + "WHERE resource_uid::jsonb = :resource_uid::jsonb";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("resource_uid", JanitorDao.serialize(resourceUid));
    return jdbcTemplate.queryForMap(sql, params);
  }

  public static List<Map<String, Object>> queryLabel(
      NamedParameterJdbcTemplate jdbcTemplate, String trackedResourceId) {

    String sql =
        "SELECT tracked_resource_id, key, value "
            + "FROM label "
            + "WHERE tracked_resource_id = :tracked_resource_id::uuid";

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("tracked_resource_id", trackedResourceId);
    return jdbcTemplate.queryForList(sql, params);
  }

  private void assertCreateResultMatch(
      TrackedResourceId trackedResourceId,
      CloudResourceUid cloudResourceUid,
      ResourceType resourceType,
      Instant creation,
      Instant expiration,
      Map<String, String> expectedLabels)
      throws JsonProcessingException {
    Map<String, Object> actual = queryTrackedResource(jdbcTemplate, cloudResourceUid);

    assertEquals(trackedResourceId.uuid().toString(), (actual.get("id")).toString());
    assertEquals(
        cloudResourceUid,
        new ObjectMapper().readValue((String) actual.get("resource_uid"), CloudResourceUid.class));

    assertEquals(resourceType.toString(), actual.get("resource_type"));
    assertEquals(creation, ((Timestamp) actual.get("creation")).toInstant());
    assertEquals(expiration, ((Timestamp) actual.get("expiration")).toInstant());

    assertEquals("READY", actual.get("state"));

    List<Map<String, Object>> actualLabel = queryLabel(jdbcTemplate, actual.get("id").toString());

    // Label
    assertEquals(expectedLabels.size(), actualLabel.size());
    Map<String, String> actualLabelMap = new HashMap<>();
    for (Map<String, Object> map : actualLabel) {
      actualLabelMap.put(map.get("key").toString(), map.get("value").toString());
    }
    assertEquals(expectedLabels, actualLabelMap);
  }
}
