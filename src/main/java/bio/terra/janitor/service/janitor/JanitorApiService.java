package bio.terra.janitor.service.janitor;

import bio.terra.janitor.common.exception.BadRequestException;
import bio.terra.janitor.db.*;
import bio.terra.janitor.generated.model.*;
import bio.terra.janitor.service.iam.AuthenticatedUserRequest;
import bio.terra.janitor.service.iam.IamService;
import com.google.common.collect.ImmutableSet;
import java.util.*;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Service handles incoming HTTP requests. */
@Component
public class JanitorApiService {
  private final IamService iamService;
  private final TrackedResourceService trackedResourceService;
  private final JanitorDao janitorDao;

  @Autowired
  public JanitorApiService(
      IamService iamService, TrackedResourceService trackedResourceService, JanitorDao janitorDao) {
    this.iamService = iamService;
    this.trackedResourceService = trackedResourceService;
    this.janitorDao = janitorDao;
  }

  public CreatedResource createResource(
      CreateResourceRequestBody body, AuthenticatedUserRequest userReq) {
    iamService.requireAdminUser(userReq);
    TrackedResource resource =
        trackedResourceService.createResource(ModelUtils.createTrackRequest(body));
    return new CreatedResource().id(resource.trackedResourceId().toString());
  }

  /** Retrieves the info about a tracked resource if there exists a resource for that id. */
  public Optional<TrackedResourceInfo> getResource(String id, AuthenticatedUserRequest userReq) {
    iamService.requireAdminUser(userReq);

    UUID uuid;
    try {
      uuid = UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      // id did not match expected UUID format.
      return Optional.empty();
    }
    TrackedResourceId trackedResourceId = TrackedResourceId.create(uuid);
    return janitorDao.retrieveResourceAndLabels(trackedResourceId).map(ModelUtils::createInfo);
  }

  /** Retrieves the resources matching the filters. */
  public TrackedResourceInfoList getResources(
      @Nullable CloudResourceUid cloudResourceUid,
      @Nullable ResourceState state,
      Integer offset,
      Integer limit,
      AuthenticatedUserRequest userReq) {
    iamService.requireAdminUser(userReq);

    TrackedResourceFilter.Builder filter =
        TrackedResourceFilter.builder()
            .cloudResourceUid(Optional.ofNullable(cloudResourceUid))
            .limit(limit)
            .offset(offset);
    if (state != null) {
      filter.allowedStates(ImmutableSet.of(ModelUtils.convert(state)));
    }
    TrackedResourceInfoList resourceList = new TrackedResourceInfoList();
    janitorDao.retrieveResourcesAndLabels(filter.build()).stream()
        .map(ModelUtils::createInfo)
        .forEach(resourceList::addResourcesItem);
    return resourceList;
  }

  /**
   * Updates the resource state.
   *
   * <p>Currently it supports:
   *
   * <ul>
   *   <li>Abandon resource: Update resource from READY or CLEANING to ABANDONED
   *   <li>Bump resource: Update resource from ABANDONED or ERROR to READY
   * </ul>
   */
  public void updateResource(
      CloudResourceUid cloudResourceUid, ResourceState state, AuthenticatedUserRequest userReq) {
    iamService.requireAdminUser(userReq);
    if (state == ResourceState.ABANDONED) {
      trackedResourceService.abandonResource(cloudResourceUid);
    } else if (state == ResourceState.READY) {
      trackedResourceService.bumpResource(cloudResourceUid);
    } else {
      throw new BadRequestException(String.format("Invalid change state: %s", state));
    }
  }

  /** Update all ERROR tracked resources to READY. */
  public void bumpErrors(AuthenticatedUserRequest userReq) {
    iamService.requireAdminUser(userReq);
    List<TrackedResource> errorResources =
        janitorDao.retrieveResourcesMatching(
            TrackedResourceFilter.builder()
                .allowedStates(ImmutableSet.of(TrackedResourceState.ERROR))
                .build());
    for (TrackedResource resource : errorResources) {
      trackedResourceService.bumpResource(resource.cloudResourceUid());
    }
  }
}
