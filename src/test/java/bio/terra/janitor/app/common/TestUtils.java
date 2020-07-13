package bio.terra.janitor.app.common;

import bio.terra.generated.model.GoogleBlobUid;
import bio.terra.generated.model.OneOfCreateResourceRequestBodyResourceUid;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

public class TestUtils {
  private static final OffsetDateTime CREATION = OffsetDateTime.now();
  private static final int TIME_TO_LIVE_MINUTE = 100;
  private static final OneOfCreateResourceRequestBodyResourceUid DEFAULT_CLOUD_RESOURCE_UID =
      new GoogleBlobUid().resourceType("googleBlobUid").blobName("blob1").bucketName("bucket1");

  private static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value2");

  public static String defaultJsonCreateRequestBody() {
    return newJsonCreateRequestBody(DEFAULT_CLOUD_RESOURCE_UID, Optional.of(DEFAULT_LABELS));
  }

  public static String newJsonCreateRequestBody(
      OneOfCreateResourceRequestBodyResourceUid cloudResourceUid,
      Optional<Map<String, String>> labels) {
    JsonObject request = new JsonObject();
    request.add("resourceUid", new Gson().toJsonTree(cloudResourceUid));
    request.addProperty("creation", CREATION.toString());
    request.addProperty("timeToLiveInMinutes", TIME_TO_LIVE_MINUTE);
    labels.ifPresent(l -> request.add("labels", new Gson().toJsonTree(l)));
    return request.toString();
  }
}
