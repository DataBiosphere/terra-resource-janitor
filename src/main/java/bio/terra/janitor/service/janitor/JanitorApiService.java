package bio.terra.janitor.service.janitor;

import bio.terra.janitor.common.exception.BadRequestException;
import bio.terra.janitor.db.*;
import bio.terra.janitor.generated.model.*;
import bio.terra.janitor.service.iam.AuthenticatedUserRequest;
import bio.terra.janitor.service.iam.IamService;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Service handles incoming HTTP requests. */
@Component
public class JanitorApiService {
  private final IamService iamService;
  private final TrackedResourceService trackedResourceService;

  @Autowired
  public JanitorApiService(IamService iamService, TrackedResourceService trackedResourceService) {
    this.iamService = iamService;
    this.trackedResourceService = trackedResourceService;
  }

  public CreatedResource createResource(
      CreateResourceRequestBody body, AuthenticatedUserRequest userReq) {
    iamService.requireAdminUser(userReq);
    return trackedResourceService.createResource(body);
  }

  /** Retrieves the info about a tracked resource if there exists a resource for that id. */
  public Optional<TrackedResourceInfo> getResource(String id, AuthenticatedUserRequest userReq) {
    iamService.requireAdminUser(userReq);
    return trackedResourceService.getResource(id);
  }

  /** Retrieves the resources with the {@link CloudResourceUid}. */
  public TrackedResourceInfoList getResources(
      CloudResourceUid cloudResourceUid, AuthenticatedUserRequest userReq) {
    iamService.requireAdminUser(userReq);
    return trackedResourceService.getResources(cloudResourceUid);
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
}
