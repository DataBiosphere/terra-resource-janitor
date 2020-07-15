package bio.terra.janitor.app.controller;

import bio.terra.generated.controller.UnauthenticatedApi;
import bio.terra.generated.model.SystemStatus;
import bio.terra.generated.model.SystemStatusSystems;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.service.stairway.StairwayComponent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class UnauthenticatedApiController implements UnauthenticatedApi {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final StairwayComponent stairwayComponent;

  @Autowired
  UnauthenticatedApiController(
      JanitorJdbcConfiguration jdbcConfiguration, StairwayComponent stairwayComponent) {
    this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
    this.stairwayComponent = stairwayComponent;
  }

  @Override
  public ResponseEntity<SystemStatus> serviceStatus() {
    // TODO add StairwayComponent to service status.
    if (jdbcTemplate.getJdbcTemplate().execute((Connection connection) -> connection.isValid(0))) {
      return new ResponseEntity<>(
          new SystemStatus()
              .ok(true)
              .putSystemsItem("postgres", new SystemStatusSystems().ok(true)),
          HttpStatus.OK);
    } else {
      return new ResponseEntity<>(
          new SystemStatus()
              .ok(false)
              .putSystemsItem("postgres", new SystemStatusSystems().ok(false)),
          HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public ResponseEntity<Void> shutdownRequest() {
    try {
      if (!stairwayComponent.shutdown()) {
        // Shutdown did not complete. Return an error so the caller knows that
        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
      }
    } catch (InterruptedException ex) {
      return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
    }
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
