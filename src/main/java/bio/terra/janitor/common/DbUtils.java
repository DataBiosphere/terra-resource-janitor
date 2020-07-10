package bio.terra.janitor.common;

import java.util.UUID;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public class DbUtils {
  /** Extract the UUID from keHolder. */
  public static UUID getUUIDField(GeneratedKeyHolder keyHolder) {
    return (UUID) keyHolder.getKeys().get("id");
  }
}
