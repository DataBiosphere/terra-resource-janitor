package bio.terra.janitor.service.janitor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.generated.model.*;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.common.NotFoundException;
import bio.terra.janitor.service.iam.AuthenticatedUserRequest;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
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
public class JanitorApiServiceTest {
  private static final OffsetDateTime DEFAULT_TIME = OffsetDateTime.now();
  private static final AuthenticatedUserRequest ADMIN_USER =
      new AuthenticatedUserRequest().email("test1@email.com");
  @Autowired private JanitorApiService janitorApiService;

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
        janitorApiService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME),
                ADMIN_USER)
            .getId();
    Map<String, String> retrievedStates =
        extractStates(janitorApiService.getResources(resourceUid, ADMIN_USER));
    assertThat(retrievedStates, Matchers.hasEntry(firstId, "READY"));
    assertThat(retrievedStates, Matchers.aMapWithSize(1));

    // Add a resource with the same CloudResourceUid that expired before the first resource.
    String secondId =
        janitorApiService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME.minusMinutes(10)),
                ADMIN_USER)
            .getId();
    retrievedStates = extractStates(janitorApiService.getResources(resourceUid, ADMIN_USER));
    assertThat(retrievedStates, Matchers.hasEntry(firstId, "READY"));
    assertThat(retrievedStates, Matchers.hasEntry(secondId, "DUPLICATED"));
    assertThat(retrievedStates, Matchers.aMapWithSize(2));

    // Add a resource with the same CloudResourceUid that expired after the first resource.
    String thirdId =
        janitorApiService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME.plusMinutes(20)),
                ADMIN_USER)
            .getId();
    retrievedStates = extractStates(janitorApiService.getResources(resourceUid, ADMIN_USER));
    assertThat(retrievedStates, Matchers.hasEntry(firstId, "DUPLICATED"));
    assertThat(retrievedStates, Matchers.hasEntry(secondId, "DUPLICATED"));
    assertThat(retrievedStates, Matchers.hasEntry(thirdId, "READY"));
    assertThat(retrievedStates, Matchers.aMapWithSize(3));
  }

  @Test
  public void abandonThenBumpResources() throws Exception {
    CloudResourceUid resourceUid =
        new CloudResourceUid()
            .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
    String firstId =
        janitorApiService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME),
                ADMIN_USER)
            .getId();

    // Add another resource with the same CloudResourceUid to verify a DUPLICATED resource not get
    // ABANDONED or BUMP
    String secondId =
        janitorApiService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME),
                ADMIN_USER)
            .getId();

    janitorApiService.updateResource(resourceUid, ResourceState.ABANDONED, ADMIN_USER);

    Map<String, String> retrievedStates =
        extractStates(janitorApiService.getResources(resourceUid, ADMIN_USER));
    assertThat(retrievedStates, Matchers.hasEntry(firstId, "ABANDONED"));
    assertThat(retrievedStates, Matchers.hasEntry(secondId, "DUPLICATED"));
    assertThat(retrievedStates, Matchers.aMapWithSize(2));

    // Bump the resource
    janitorApiService.updateResource(resourceUid, ResourceState.READY, ADMIN_USER);

    retrievedStates = extractStates(janitorApiService.getResources(resourceUid, ADMIN_USER));
    assertThat(retrievedStates, Matchers.hasEntry(firstId, "READY"));
    assertThat(retrievedStates, Matchers.hasEntry(secondId, "DUPLICATED"));
    assertThat(retrievedStates, Matchers.aMapWithSize(2));
  }

  @Test
  public void abandonResources_notFound() throws Exception {
    CloudResourceUid resourceUid =
        new CloudResourceUid()
            .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
    assertThrows(
        NotFoundException.class,
        () -> janitorApiService.updateResource(resourceUid, ResourceState.ABANDONED, ADMIN_USER));
  }

  /** Gets NotFoundException exception when resource exists but not in ABANDONED state. */
  @Test
  public void bumpResources_notFound() throws Exception {
    CloudResourceUid resourceUid =
        new CloudResourceUid()
            .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
    assertThrows(
        NotFoundException.class,
        () -> janitorApiService.updateResource(resourceUid, ResourceState.READY, ADMIN_USER));
  }
}
