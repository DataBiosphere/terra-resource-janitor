package bio.terra.janitor.app.controller;

import bio.terra.generated.controller.UnauthenticatedApi;
import bio.terra.generated.model.SystemStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class UnauthenticatedApiController implements UnauthenticatedApi {
  @Override
  public ResponseEntity<SystemStatus> serviceStatus() {
    return new ResponseEntity<>(new SystemStatus().ok(true), HttpStatus.OK);
  }
}
