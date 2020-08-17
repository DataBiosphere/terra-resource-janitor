package bio.terra.janitor.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
public class TrackedResourceIdTest {
  @Test
  public void serialize() throws Exception {
    UUID id = UUID.randomUUID();
    TrackedResourceId trackedResourceId = TrackedResourceId.create(id);
    assertEquals(
        "[\"bio.terra.janitor.db.AutoValue_TrackedResourceId\",{\"uuid\":\""
            + id.toString()
            + "\"}]",
        new ObjectMapper().writeValueAsString(trackedResourceId));
  }
}
