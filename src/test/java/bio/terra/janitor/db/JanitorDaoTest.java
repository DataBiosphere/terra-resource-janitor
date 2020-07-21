package bio.terra.janitor.db;

import static bio.terra.janitor.db.JanitorDao.serialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.GoogleProjectUid;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.common.ResourceType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

  private NamedParameterJdbcTemplate jdbcTemplate;

  @BeforeEach
  public void setup() {
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  @Test
  public void createTrackedResource() throws Exception {
    CloudResourceUid cloudResourceUid =
        new CloudResourceUid()
            .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
    TrackedResourceId trackedResourceId =
        janitorDao.createResource(cloudResourceUid, DEFAULT_LABELS, CREATION, EXPIRATION);

    assertCreateResultMatch(
        trackedResourceId,
        cloudResourceUid,
        ResourceType.GOOGLE_PROJECT,
        CREATION,
        EXPIRATION,
        DEFAULT_LABELS);
  }

  @Test
  public void serializeCloudResourceUid() {
    assertEquals(
        "{\"googleProjectUid\":{\"projectId\":\"my-project\"},\"googleBigQueryDatasetUid\":null,\"googleBigQueryTableUid\":null,\"googleBlobUid\":null,\"googleBucketUid\":null}",
        serialize(
            new CloudResourceUid()
                .googleProjectUid(new GoogleProjectUid().projectId("my-project"))));
  }

  public static Map<String, Object> queryTrackedResource(
      NamedParameterJdbcTemplate jdbcTemplate, CloudResourceUid resourceUid) {
    String sql =
        "SELECT id, resource_uid::text, resource_type, creation, expiration, state FROM tracked_resource WHERE resource_uid::jsonb @> :resource_uid::jsonb";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("resource_uid", serialize(resourceUid));
    return jdbcTemplate.queryForMap(sql, params);
  }

  /**
   * Query by CloudResourceUid but not use the default serialize method which includes null value to
   * verify query works.
   */
  public static Map<String, Object> queryTrackedResourceNonNull(
      NamedParameterJdbcTemplate jdbcTemplate, CloudResourceUid resourceUid)
      throws JsonProcessingException {
    ObjectMapper mapper =
        new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    String sql =
        "SELECT id, resource_uid::text, resource_type, creation, expiration, state "
            + "FROM tracked_resource "
            + "WHERE jsonb_strip_nulls(resource_uid)::text = :resource_uid::text";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("resource_uid", mapper.writeValueAsString(resourceUid));
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

    assertEquals(trackedResourceId.id().toString(), (actual.get("id")).toString());
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

    // Verify query works without null values in json string.
    assertNotNull(queryTrackedResourceNonNull(jdbcTemplate, cloudResourceUid));
  }
}
