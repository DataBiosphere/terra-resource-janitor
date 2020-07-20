package bio.terra.janitor.app.common;

import static bio.terra.janitor.common.JanitorObjectMapperHelper.serializeCloudResourceUid;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.GoogleProjectUid;
import bio.terra.janitor.app.Main;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
public class JanitorObjectMapperHelper {
  @Test
  public void testSerializeCloudResourceUid() {
    assertEquals(
        "{\"googleProjectUid\":{\"projectId\":\"my-project\"},\"googleBigQueryDatasetUid\":null,\"googleBigQueryTableUid\":null,\"googleBlobUid\":null,\"googleBucketUid\":null}",
        serializeCloudResourceUid(
            new CloudResourceUid()
                .googleProjectUid(new GoogleProjectUid().projectId("my-project"))));
  }
}
