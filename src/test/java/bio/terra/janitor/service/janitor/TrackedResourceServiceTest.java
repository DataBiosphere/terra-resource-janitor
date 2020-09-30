package bio.terra.janitor.service.janitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.janitor.common.BaseUnitTest;
import bio.terra.janitor.common.NotFoundException;
import bio.terra.janitor.db.*;
import bio.terra.janitor.generated.model.*;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.List;
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
  private static final Instant DEFAULT_TIME = Instant.now();
  @Autowired private TrackedResourceService trackedResourceService;
  @Autowired private JanitorDao janitorDao;

  private static CloudResourceUid createUniqueId() {
    return new CloudResourceUid()
        .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
  }

  /** Returns a map of the resource ids to their {@link ResourceState}s. */
  private static Map<TrackedResourceId, TrackedResourceState> extractStates(
      List<TrackedResource> resources) {
    return resources.stream()
        .collect(
            Collectors.toMap(
                TrackedResource::trackedResourceId, TrackedResource::trackedResourceState));
  }

  private static TrackedResourceFilter filterOf(CloudResourceUid resourceUid) {
    return TrackedResourceFilter.builder().cloudResourceUid(resourceUid).build();
  }

  @Test
  public void createResource_Duplicates() {
    CloudResourceUid resourceUid = createUniqueId();
    TrackedResourceId firstId =
        trackedResourceService
            .createResource(
                TrackRequest.builder()
                    .cloudResourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME)
                    .build())
            .trackedResourceId();
    assertEquals(
        ImmutableMap.of(firstId, TrackedResourceState.READY),
        extractStates(janitorDao.retrieveResourcesMatching(filterOf(resourceUid))));

    // Add a resource with the same CloudResourceUid that expired before the first resource.
    TrackedResourceId secondId =
        trackedResourceService
            .createResource(
                TrackRequest.builder()
                    .cloudResourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME.minusSeconds(10))
                    .build())
            .trackedResourceId();
    assertEquals(
        ImmutableMap.of(
            firstId, TrackedResourceState.READY, secondId, TrackedResourceState.DUPLICATED),
        extractStates(janitorDao.retrieveResourcesMatching(filterOf(resourceUid))));

    // Add a resource with the same CloudResourceUid that expired after the first resource.
    TrackedResourceId thirdId =
        trackedResourceService
            .createResource(
                TrackRequest.builder()
                    .cloudResourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME.plusSeconds(20))
                    .build())
            .trackedResourceId();
    assertEquals(
        ImmutableMap.of(
            firstId,
            TrackedResourceState.DUPLICATED,
            secondId,
            TrackedResourceState.DUPLICATED,
            thirdId,
            TrackedResourceState.READY),
        extractStates(janitorDao.retrieveResourcesMatching(filterOf(resourceUid))));
  }

  @Test
  public void abandonThenBumpResources() {
    CloudResourceUid resourceUid = createUniqueId();
    TrackedResourceId firstId =
        trackedResourceService
            .createResource(
                TrackRequest.builder()
                    .cloudResourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME)
                    .build())
            .trackedResourceId();

    // Add another resource with the same CloudResourceUid to verify a DUPLICATED resource not get
    // ABANDONED or BUMP
    TrackedResourceId secondId =
        trackedResourceService
            .createResource(
                TrackRequest.builder()
                    .cloudResourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME)
                    .build())
            .trackedResourceId();

    trackedResourceService.abandonResource(resourceUid);

    assertEquals(
        ImmutableMap.of(
            firstId, TrackedResourceState.ABANDONED, secondId, TrackedResourceState.DUPLICATED),
        extractStates(janitorDao.retrieveResourcesMatching(filterOf(resourceUid))));

    // Bump the resource
    trackedResourceService.bumpResource(resourceUid);

    assertEquals(
        ImmutableMap.of(
            firstId, TrackedResourceState.READY, secondId, TrackedResourceState.DUPLICATED),
        extractStates(janitorDao.retrieveResourcesMatching(filterOf(resourceUid))));
  }

  @Test
  public void abandonResource_cleaning() {
    CloudResourceUid resourceUid = createUniqueId();
    TrackedResourceId cleaningId =
        trackedResourceService
            .createResource(
                TrackRequest.builder()
                    .cloudResourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME)
                    .build())
            .trackedResourceId();
    janitorDao.updateResourceState(cleaningId, TrackedResourceState.CLEANING);

    assertEquals(
        ImmutableMap.of(cleaningId, TrackedResourceState.CLEANING),
        extractStates(janitorDao.retrieveResourcesMatching(filterOf(resourceUid))));
    trackedResourceService.abandonResource(resourceUid);
    assertEquals(
        ImmutableMap.of(cleaningId, TrackedResourceState.ABANDONED),
        extractStates(janitorDao.retrieveResourcesMatching(filterOf(resourceUid))));
  }

  @Test
  public void abandonResource_error() {
    CloudResourceUid resourceUid = createUniqueId();
    TrackedResourceId errorId =
        trackedResourceService
            .createResource(
                TrackRequest.builder()
                    .cloudResourceUid(resourceUid)
                    .creation(DEFAULT_TIME)
                    .expiration(DEFAULT_TIME)
                    .build())
            .trackedResourceId();
    janitorDao.updateResourceState(errorId, TrackedResourceState.ERROR);

    assertEquals(
        ImmutableMap.of(errorId, TrackedResourceState.ERROR),
        extractStates(janitorDao.retrieveResourcesMatching(filterOf(resourceUid))));
    trackedResourceService.abandonResource(resourceUid);
    assertEquals(
        ImmutableMap.of(errorId, TrackedResourceState.ABANDONED),
        extractStates(janitorDao.retrieveResourcesMatching(filterOf(resourceUid))));
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
