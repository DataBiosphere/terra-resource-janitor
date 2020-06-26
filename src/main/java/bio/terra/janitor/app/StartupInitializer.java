package bio.terra.janitor.app;

import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.service.migirate.MigrateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

public final class StartupInitializer {
  private static final Logger logger = LoggerFactory.getLogger(StartupInitializer.class);
  private static final String changelogPath = "db/changelog.xml";

  public static void initialize(ApplicationContext applicationContext) {
    // Initialize or upgrade the database depending on the configuration
    MigrateService migrateService = (MigrateService) applicationContext.getBean("migrateService");
    JanitorJdbcConfiguration janitorJdbcConfiguration =
        (JanitorJdbcConfiguration) applicationContext.getBean("janitorJdbcConfiguration");

    if (janitorJdbcConfiguration.isRecreateDbOnStart()) {
      migrateService.initialize(changelogPath, janitorJdbcConfiguration.getDataSource());
    } else if (janitorJdbcConfiguration.isUpdateDbOnStart()) {
      migrateService.upgrade(changelogPath, janitorJdbcConfiguration.getDataSource());
    }
  }
}
