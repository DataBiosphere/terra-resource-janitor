package bio.terra.janitor.app.common;

import static bio.terra.janitor.common.JanitorObjectMapperHelper.serializeCloudResourceUid;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.GoogleProjectUid;
import bio.terra.janitor.common.ResourceType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class TestUtils {
  private static final String READ_TRACKED_RESOURCE_SQL =
      "SELECT id, resource_uid::text, resource_type, creation, expiration, state FROM tracked_resource WHERE resource_uid::jsonb @> :resource_uid::jsonb";

  private static final String READ_LABEL_SQL =
      "SELECT tracked_resource_id, key, value FROM label WHERE tracked_resource_id = :tracked_resource_id::uuid";

  public static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value2");

  public static final Instant CREATION = Instant.now();
  public static final int TIME_TO_LIVE_MINUTE = 100;
  public static final Instant EXPIRATION = CREATION.plus(TIME_TO_LIVE_MINUTE, ChronoUnit.MINUTES);

  public static CloudResourceUid newGoogleProjectResourceUid() {
    return new CloudResourceUid()
        .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
  }

  public static String newJsonCreateRequestBody(
      CloudResourceUid cloudResourceUid, Optional<Map<String, String>> labels) {
    ObjectMapper mapper = new ObjectMapper();

    ObjectNode trackedResourceNode =
        mapper.createObjectNode().put("timeToLiveInMinutes", TIME_TO_LIVE_MINUTE);
    trackedResourceNode.set("resourceUid", mapper.valueToTree(cloudResourceUid));
    labels.ifPresent(
        l -> {
          trackedResourceNode.set("labels", mapper.valueToTree(l));
        });
    return trackedResourceNode.toString();
  }

  public static Map<String, Object> queryTrackedResource(
      NamedParameterJdbcTemplate jdbcTemplate, CloudResourceUid resourceUid) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("resource_uid", serializeCloudResourceUid(resourceUid));
    return jdbcTemplate.queryForMap(READ_TRACKED_RESOURCE_SQL, params);
  }

  public static List<Map<String, Object>> queryLabel(
      NamedParameterJdbcTemplate jdbcTemplate, String trackedResourceId) {
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("tracked_resource_id", trackedResourceId);
    return jdbcTemplate.queryForList(READ_LABEL_SQL, params);
  }

  public static void assertCreateResultMatch(
      CloudResourceUid cloudResourceUid,
      ResourceType resourceType,
      Optional<Instant> creation,
      Optional<Instant> expiration,
      int timeToLiveInMinutes,
      NamedParameterJdbcTemplate jdbcTemplate,
      Map<String, String> expectedLabels)
      throws JsonProcessingException {
    Map<String, Object> actual = queryTrackedResource(jdbcTemplate, cloudResourceUid);

    assertEquals(
        cloudResourceUid,
        new ObjectMapper().readValue((String) actual.get("resource_uid"), CloudResourceUid.class));
    assertEquals(resourceType.toString(), actual.get("resource_type"));
    creation.ifPresent(
        time -> assertEquals(time, ((Timestamp) actual.get("creation")).toInstant()));
    expiration.ifPresent(
        time -> assertEquals(time, ((Timestamp) actual.get("expiration")).toInstant()));
    assertEquals(
        timeToLiveInMinutes,
        Duration.between(
                ((Timestamp) actual.get("creation")).toInstant(),
                ((Timestamp) actual.get("expiration")).toInstant())
            .toMinutes());

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
