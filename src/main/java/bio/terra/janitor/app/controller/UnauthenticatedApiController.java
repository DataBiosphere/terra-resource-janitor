package bio.terra.janitor.app.controller;

import bio.terra.generated.controller.UnauthenticatedApi;
import bio.terra.generated.model.SystemStatus;
import bio.terra.generated.model.SystemStatusSystems;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import java.sql.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class UnauthenticatedApiController implements UnauthenticatedApi {
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  UnauthenticatedApiController(JanitorJdbcConfiguration jdbcConfiguration) {
    this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  @Override
  public ResponseEntity<SystemStatus> serviceStatus() {
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
}
