package bio.terra.janitor.service.janitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.janitor.common.BaseUnitTest;
import bio.terra.janitor.common.NotFoundException;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.TrackedResourceId;
import bio.terra.janitor.db.TrackedResourceState;
import bio.terra.janitor.generated.model.*;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class TrackedResourceServiceTest extends BaseUnitTest {
  private static final OffsetDateTime DEFAULT_TIME = OffsetDateTime.now();
  @Autowired private TrackedResourceService trackedResourceService;
  @Autowired private JanitorDao janitorDao;

  private static CloudResourceUid createUniqueId() {
    return new CloudResourceUid()
        .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
  }

  /** Returns a map of the resource ids to their TrackedResourceStates as strings. */
  private static Map<String, String> extractStates(TrackedResourceInfoList resourceList) {
    return resourceList.getResources().stream()
        .collect(Collectors.toMap(TrackedResourceInfo::getId, TrackedResourceInfo::getState));
  }

  @Test
  public void createResource_Duplicates() {
    CloudResourceUid resourceUid = createUniqueId();
    String firstId =
        trackedResourceService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME))
            .getId();
    assertEquals(
        ImmutableMap.of(firstId, "READY"),
        extractStates(trackedResourceService.getResources(resourceUid)));

    // Add a resource with the same CloudResourceUid that expired before the first resource.
    String secondId =
        trackedResourceService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME.minusMinutes(10)))
            .getId();
    assertEquals(
        ImmutableMap.of(firstId, "READY", secondId, "DUPLICATED"),
        extractStates(trackedResourceService.getResources(resourceUid)));

    // Add a resource with the same CloudResourceUid that expired after the first resource.
    String thirdId =
        trackedResourceService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME.plusMinutes(20)))
            .getId();
    assertEquals(
        ImmutableMap.of(firstId, "DUPLICATED", secondId, "DUPLICATED", thirdId, "READY"),
        extractStates(trackedResourceService.getResources(resourceUid)));
  }

  @Test
  public void abandonThenBumpResources() {
    CloudResourceUid resourceUid = createUniqueId();
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

    assertEquals(
        ImmutableMap.of(firstId, "ABANDONED", secondId, "DUPLICATED"),
        extractStates(trackedResourceService.getResources(resourceUid)));

    // Bump the resource
    trackedResourceService.bumpResource(resourceUid);

    assertEquals(
        ImmutableMap.of(firstId, "READY", secondId, "DUPLICATED"),
        extractStates(trackedResourceService.getResources(resourceUid)));
  }

  @Test
  public void abandonResource_cleaning() {
    CloudResourceUid resourceUid = createUniqueId();
    String cleaningId =
        trackedResourceService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME))
            .getId();
    janitorDao.updateResourceState(
        TrackedResourceId.create(UUID.fromString(cleaningId)), TrackedResourceState.CLEANING);

    assertEquals(
        ImmutableMap.of(cleaningId, "CLEANING"),
        extractStates(trackedResourceService.getResources(resourceUid)));
    trackedResourceService.abandonResource(resourceUid);
    assertEquals(
        ImmutableMap.of(cleaningId, "ABANDONED"),
        extractStates(trackedResourceService.getResources(resourceUid)));
  }

  @Test
  public void abandonResource_error() {
    CloudResourceUid resourceUid = createUniqueId();
    String errorId =
        trackedResourceService
            .createResource(
                new CreateResourceRequestBody()
                    .resourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME))
            .getId();
    janitorDao.updateResourceState(
        TrackedResourceId.create(UUID.fromString(errorId)), TrackedResourceState.ERROR);

    assertEquals(
        ImmutableMap.of(errorId, "ERROR"),
        extractStates(trackedResourceService.getResources(resourceUid)));
    trackedResourceService.abandonResource(resourceUid);
    assertEquals(
        ImmutableMap.of(errorId, "ABANDONED"),
        extractStates(trackedResourceService.getResources(resourceUid)));
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
