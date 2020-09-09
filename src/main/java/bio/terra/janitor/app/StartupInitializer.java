package bio.terra.janitor.app;

import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.service.cleanup.FlightScheduler;
import bio.terra.janitor.service.migirate.MigrateService;
import bio.terra.janitor.service.pubsub.TrackedResourceSubscriber;
import bio.terra.janitor.service.stackdriver.StatsExporter;
import bio.terra.janitor.service.stairway.StairwayComponent;
import org.springframework.context.ApplicationContext;

/**
 * Initializes the application after the application is setup, but before the port is opened for
 * business. The purpose for this class is for database initialization and migration.
 */
public final class StartupInitializer {
  private static final String changelogPath = "db/changelog.xml";

  public static void initialize(ApplicationContext applicationContext) {
    applicationContext.getBean(StatsExporter.class).initialize();
    // Initialize or upgrade the database depending on the configuration
    MigrateService migrateService = applicationContext.getBean(MigrateService.class);
    JanitorJdbcConfiguration janitorJdbcConfiguration =
        applicationContext.getBean(JanitorJdbcConfiguration.class);

    if (janitorJdbcConfiguration.isRecreateDbOnStart()) {
      migrateService.initialize(changelogPath, janitorJdbcConfiguration.getDataSource());
    } else if (janitorJdbcConfiguration.isUpdateDbOnStart()) {
      migrateService.upgrade(changelogPath, janitorJdbcConfiguration.getDataSource());
    }
    applicationContext.getBean(StairwayComponent.class).initialize();
    applicationContext.getBean(FlightScheduler.class).initialize();
    applicationContext.getBean(TrackedResourceSubscriber.class).initialize();
  }
}
