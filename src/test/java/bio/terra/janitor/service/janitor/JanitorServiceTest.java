package bio.terra.janitor.service.janitor;

import static org.hamcrest.MatcherAssert.assertThat;

import bio.terra.generated.model.*;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.service.iam.AuthenticatedUserRequest;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class JanitorServiceTest {
  private static final OffsetDateTime DEFAULT_TIME = OffsetDateTime.now();
  private static final AuthenticatedUserRequest ADMIN_USER =
      new AuthenticatedUserRequest().email("test1@email.com");
  @Autowired private JanitorService janitorService;

  /** Returns a map of the resource ids to their TrackedResourceStates as strings. */
  private static Map<String, String> extractStates(TrackedResourceInfoList resourceList) {
    return resourceList.getResources().stream()
        .collect(Collectors.toMap(TrackedResourceInfo::getId, TrackedResourceInfo::getState));
  }

  @Test
  public void createResource_Duplicates() {
    CloudResourceUid resourceUid =
        new CloudResourceUid().googleBucketUid(new GoogleBucketUid().bucketName("foo"));

    String firstId =
        janitorService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME),
                ADMIN_USER)
            .getId();
    Map<String, String> retrievedStates =
        extractStates(janitorService.getResources(resourceUid, ADMIN_USER));
    assertThat(retrievedStates, Matchers.hasEntry(firstId, "READY"));
    assertThat(retrievedStates, Matchers.aMapWithSize(1));

    // Add a resource with the same CloudResourceUid that expired before the first resource.
    String secondId =
        janitorService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME.minusMinutes(10)),
                ADMIN_USER)
            .getId();
    retrievedStates = extractStates(janitorService.getResources(resourceUid, ADMIN_USER));
    assertThat(retrievedStates, Matchers.hasEntry(firstId, "READY"));
    assertThat(retrievedStates, Matchers.hasEntry(secondId, "DUPLICATED"));
    assertThat(retrievedStates, Matchers.aMapWithSize(2));

    // Add a resource with the same CloudResourceUid that expired after the first resource.
    String thirdId =
        janitorService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME.plusMinutes(20)),
                ADMIN_USER)
            .getId();
    retrievedStates = extractStates(janitorService.getResources(resourceUid, ADMIN_USER));
    assertThat(retrievedStates, Matchers.hasEntry(firstId, "DUPLICATED"));
    assertThat(retrievedStates, Matchers.hasEntry(secondId, "DUPLICATED"));
    assertThat(retrievedStates, Matchers.hasEntry(thirdId, "READY"));
    assertThat(retrievedStates, Matchers.aMapWithSize(3));
  }
}
