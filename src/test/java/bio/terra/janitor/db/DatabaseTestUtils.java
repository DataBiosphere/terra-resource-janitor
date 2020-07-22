package bio.terra.janitor.db;

import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.service.migirate.MigrateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Test utils for databases. */
@Component
public class DatabaseTestUtils {
  private static final String JANITOR_CHANGELOG_PATH = "db/changelog.xml";
  @Autowired MigrateService migrateService;

  @Autowired JanitorJdbcConfiguration janitorJdbcConfiguration;

  /** Drops and recreates the Janitor database tables. */
  public void resetJanitorDb() {
    migrateService.initialize(JANITOR_CHANGELOG_PATH, janitorJdbcConfiguration.getDataSource());
  }
}
