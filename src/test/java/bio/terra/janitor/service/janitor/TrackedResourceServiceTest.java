package bio.terra.janitor.service.janitor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.generated.model.*;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.common.NotFoundException;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("unit")
@ActiveProfiles({"test", "unit"})
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class TrackedResourceServiceTest {
  private static final OffsetDateTime DEFAULT_TIME = OffsetDateTime.now();
  @Autowired private TrackedResourceService trackedResourceService;

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
        trackedResourceService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME))
            .getId();
    Map<String, String> retrievedStates =
        extractStates(trackedResourceService.getResources(resourceUid));
    assertThat(retrievedStates, Matchers.hasEntry(firstId, "READY"));
    assertThat(retrievedStates, Matchers.aMapWithSize(1));

    // Add a resource with the same CloudResourceUid that expired before the first resource.
    String secondId =
        trackedResourceService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME.minusMinutes(10)))
            .getId();
    retrievedStates = extractStates(trackedResourceService.getResources(resourceUid));
    assertThat(retrievedStates, Matchers.hasEntry(firstId, "READY"));
    assertThat(retrievedStates, Matchers.hasEntry(secondId, "DUPLICATED"));
    assertThat(retrievedStates, Matchers.aMapWithSize(2));

    // Add a resource with the same CloudResourceUid that expired after the first resource.
    String thirdId =
        trackedResourceService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME.plusMinutes(20)))
            .getId();
    retrievedStates = extractStates(trackedResourceService.getResources(resourceUid));
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
        trackedResourceService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME))
            .getId();

    // Add another resource with the same CloudResourceUid to verify a DUPLICATED resource not get
    // ABANDONED or BUMP
    String secondId =
        trackedResourceService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME))
            .getId();

    trackedResourceService.abandonResource(resourceUid);

    Map<String, String> retrievedStates =
        extractStates(trackedResourceService.getResources(resourceUid));
    assertThat(retrievedStates, Matchers.hasEntry(firstId, "ABANDONED"));
    assertThat(retrievedStates, Matchers.hasEntry(secondId, "DUPLICATED"));
    assertThat(retrievedStates, Matchers.aMapWithSize(2));

    // Bump the resource
    trackedResourceService.bumpResource(resourceUid);

    retrievedStates = extractStates(trackedResourceService.getResources(resourceUid));
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
        NotFoundException.class, () -> trackedResourceService.abandonResource(resourceUid));
  }

  /** Gets NotFoundException exception when resource exists but not in ABANDONED state. */
  @Test
  public void bumpResources_notFound() throws Exception {
    CloudResourceUid resourceUid =
        new CloudResourceUid()
            .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
    assertThrows(NotFoundException.class, () -> trackedResourceService.bumpResource(resourceUid));
  }
}
