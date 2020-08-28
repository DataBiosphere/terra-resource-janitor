package bio.terra.janitor.app.controller;

import bio.terra.generated.controller.JanitorApi;
import bio.terra.generated.model.*;
import bio.terra.janitor.service.iam.AuthenticatedUserRequest;
import bio.terra.janitor.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.janitor.service.janitor.JanitorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class JanitorApiController implements JanitorApi {
  private final JanitorService janitorService;
  private final HttpServletRequest request;
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;

  @Autowired
  public JanitorApiController(
      JanitorService janitorService,
      HttpServletRequest request,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory) {
    this.janitorService = janitorService;
    this.request = request;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
  }

  private AuthenticatedUserRequest getAuthenticatedRequest() {
    return authenticatedUserRequestFactory.from(request);
  }

  @Override
  public ResponseEntity<TrackedResourceInfo> getResource(String id) {
    Optional<TrackedResourceInfo> resource =
        janitorService.getResource(id, getAuthenticatedRequest());
    return resource
        .map(trackedResourceInfo -> new ResponseEntity<>(trackedResourceInfo, HttpStatus.OK))
        .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }

  @Override
  public ResponseEntity<TrackedResourceInfoList> getResources(
      @NotNull @Valid CloudResourceUid cloudResourceUid) {
    return new ResponseEntity<>(
        janitorService.getResources(cloudResourceUid, getAuthenticatedRequest()), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<CreatedResource> createResource(
      @Valid @RequestBody CreateResourceRequestBody body) {
    return new ResponseEntity<>(
        janitorService.createResource(body, getAuthenticatedRequest()), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateResource(
      @NotNull @Valid CloudResourceUid cloudResourceUid, @NotNull @Valid ResourceState state) {
    janitorService.updateResource(cloudResourceUid, state, getAuthenticatedRequest());
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  /** Required if using Swagger-CodeGen, but actually we don't need this. */
  @Override
  public Optional<ObjectMapper> getObjectMapper() {
    return Optional.empty();
  }

  /** Required if using Swagger-CodeGen, but actually we don't need this. */
  @Override
  public Optional<HttpServletRequest> getRequest() {
    return Optional.empty();
  }
}
