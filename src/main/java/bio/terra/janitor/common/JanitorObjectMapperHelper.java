package bio.terra.janitor.common;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.common.exception.InvalidResourceUidException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Helper function to wrap {@link ObjectMapper}. */
public class JanitorObjectMapperHelper {
  public static String serializeCloudResourceUid(CloudResourceUid resource) {
    try {
      return new ObjectMapper().writeValueAsString(resource);
    } catch (JsonProcessingException e) {
      throw new InvalidResourceUidException("Failed to serialize CloudResourceUid: " + resource);
    }
  }
}
