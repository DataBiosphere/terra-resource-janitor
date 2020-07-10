package bio.terra.janitor.app.controller;

import bio.terra.generated.controller.JanitorApi;
import bio.terra.generated.model.CreateResourceRequestBody;
import bio.terra.generated.model.CreatedResource;
import bio.terra.generated.model.ResourceDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class JanitorApiController implements JanitorApi {
  @Override
  public ResponseEntity<CreatedResource> createResource(@Valid CreateResourceRequestBody body) {
    // TODO(CA-873): Implement this.
    return null;
  }

  @Override
  public ResponseEntity<ResourceDescription> getResource(String id) {
    return new ResponseEntity<>(new ResourceDescription().id(id), HttpStatus.OK);
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
