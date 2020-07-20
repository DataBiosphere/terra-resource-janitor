package bio.terra.janitor.app.common;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.GoogleProjectUid;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class TestUtils {
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
}
