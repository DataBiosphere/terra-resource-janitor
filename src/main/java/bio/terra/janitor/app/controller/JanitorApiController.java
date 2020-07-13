package bio.terra.janitor.app.controller;

import bio.terra.generated.controller.JanitorApi;
import bio.terra.generated.model.CreateResourceRequestBody;
import bio.terra.generated.model.CreatedResource;
import bio.terra.generated.model.ResourceDescription;
import bio.terra.janitor.service.janitor.JanitorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class JanitorApiController implements JanitorApi {

  private final JanitorService janitorService;

  @Autowired
  public JanitorApiController(JanitorService janitorService) {
    this.janitorService = janitorService;
  }

  @Override
  public ResponseEntity<ResourceDescription> getResource(String id) {
    return new ResponseEntity<>(new ResourceDescription().id(id), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<CreatedResource> createResource(
      @RequestBody CreateResourceRequestBody body) {
    System.out.println("~~~~~~~~~HERE!!!");
    return new ResponseEntity<>(janitorService.createResource(body), HttpStatus.OK);
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
