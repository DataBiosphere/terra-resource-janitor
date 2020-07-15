package bio.terra.janitor.app.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.generated.model.GoogleBlobUid;
import bio.terra.generated.model.OneOfCloudResourceUid;
import bio.terra.janitor.common.CloudResourceType;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class TestUtils {
  private static final OffsetDateTime CREATION = OffsetDateTime.now();
  private static final int TIME_TO_LIVE_MINUTE = 100;

  private static final String READ_TRACKED_RESOURCE_SQL =
      "SELECT id, resource_uid::text, resource_type, creation, expiration, state FROM tracked_resource WHERE resource_uid::text = :resource_uid";

  private static final String READ_LABEL_SQL =
      "SELECT id, resource_uid::text, key, value FROM label WHERE resource_uid::text = :resource_uid";

  private static final String READ_Test = "SELECT * FROM label";

  public static final OneOfCloudResourceUid DEFAULT_CLOUD_RESOURCE_UID =
      new GoogleBlobUid().resourceType("googleBlobUid").blobName("blob1").bucketName("bucket1");

  public static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value2");

  public static final OffsetDateTime DEFAULT_CREATION_TIME = OffsetDateTime.now();

  public static final OffsetDateTime DEFAULT_EXPIRATION_TIME = DEFAULT_CREATION_TIME.plusMinutes(5);

  public static String defaultJsonCreateRequestBody() {
    return newJsonCreateRequestBody(DEFAULT_CLOUD_RESOURCE_UID, Optional.of(DEFAULT_LABELS));
  }

  public static String newJsonCreateRequestBody(
      OneOfCloudResourceUid cloudResourceUid, Optional<Map<String, String>> labels) {
    JsonObject request = new JsonObject();
    // request.add("resourceUid", new Gson().toJsonTree(cloudResourceUid));
    request.addProperty("creation", CREATION.toString());
    request.addProperty("timeToLiveInMinutes", TIME_TO_LIVE_MINUTE);
    labels.ifPresent(l -> request.add("labels", new Gson().toJsonTree(l)));
    return request.toString();
  }

  public static Map<String, Object> queryTrackedResource(
      NamedParameterJdbcTemplate jdbcTemplate, String resourceUid) {
    System.out.println("~~~~~!!!!!!!!!4444444");
    System.out.println("~~~~~!!!!!!!!!");
    System.out.println(jdbcTemplate.queryForList(READ_Test, new HashMap<>()));
    System.out.println(resourceUid);
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("resource_uid", resourceUid);
    return jdbcTemplate.queryForMap(READ_TRACKED_RESOURCE_SQL, params);
  }

  public static List<Map<String, Object>> queryLabel(
      NamedParameterJdbcTemplate jdbcTemplate, String resourceUid) {
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("resource_uid", resourceUid);
    return jdbcTemplate.queryForList(READ_LABEL_SQL, params);
  }

  public static void assertTrackedResourceMatch(
      String cloudResourceUid,
      CloudResourceType.Enum resourceType,
      OffsetDateTime creation,
      OffsetDateTime expiration,
      NamedParameterJdbcTemplate jdbcTemplate) {
    Map<String, Object> actual = queryTrackedResource(jdbcTemplate, cloudResourceUid);
    assertEquals(cloudResourceUid, actual.get("resource_uid"));
    assertEquals(resourceType, actual.get("resource_type"));
    assertEquals(creation, actual.get("creation"));
    assertEquals(expiration, actual.get("expiration"));
    assertEquals("READY", actual.get("state"));
  }

  public static void assertLabelMatch(
      String cloudResourceUid,
      Map<String, String> expected,
      NamedParameterJdbcTemplate jdbcTemplate) {
    List<Map<String, Object>> actual = queryLabel(jdbcTemplate, cloudResourceUid);

    assertEquals(expected.size(), actual.size());
    actual.forEach(map -> assertEquals(cloudResourceUid, map.get("resource_uid")));
    Map<String, String> actualLabelMap = new HashMap<>();
    for (Map<String, Object> map : actual) {
      actualLabelMap.put(map.get("key").toString(), map.get("value").toString());
    }
    assertEquals(expected, actualLabelMap);
  }
}
