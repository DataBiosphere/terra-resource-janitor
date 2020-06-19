package bio.terra.janitor.app.controller;

import bio.terra.generated.controller.JanitorApi;
import bio.terra.generated.model.ResourceDescription;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class JanitorApiController implements JanitorApi {

  @Override
  public ResponseEntity<ResourceDescription> getResource(String id) {
    return new ResponseEntity<>(new ResourceDescription().id(id), HttpStatus.OK);
  }
}
