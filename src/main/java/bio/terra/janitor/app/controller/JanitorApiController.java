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

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  @Override
  public ResponseEntity<TrackedResourceInfo> getResource(String id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    Optional<TrackedResourceInfo> resource = janitorService.getResource(id, userReq);
    if (resource.isPresent()) {
      return new ResponseEntity<>(resource.get(), HttpStatus.OK);
    } else {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
  }

  @Override
  public ResponseEntity<TrackedResourceInfoList> getResources(
      @NotNull @Valid CloudResourceUid cloudResourceUid) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    return new ResponseEntity<>(
        janitorService.getResources(cloudResourceUid, userReq), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<CreatedResource> createResource(
      @Valid @RequestBody CreateResourceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    return new ResponseEntity<>(janitorService.createResource(body, userReq), HttpStatus.OK);
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
