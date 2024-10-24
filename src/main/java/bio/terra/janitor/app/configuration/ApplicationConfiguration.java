package bio.terra.janitor.app.configuration;

import static bio.terra.janitor.app.configuration.BeanNames.*;

import bio.terra.janitor.app.StartupInitializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class ApplicationConfiguration {
  @Bean(JDBC_TEMPLATE)
  public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(JanitorJdbcConfiguration config) {
    return new NamedParameterJdbcTemplate(config.getDataSource());
  }

  @Bean(OBJECT_MAPPER)
  public ObjectMapper objectMapper() {
    return new ObjectMapper()
        .registerModule(new ParameterNamesModule())
        .registerModule(new Jdk8Module())
        .registerModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_ABSENT);
  }

  /**
   * This is a "magic bean": It supplies a method that Spring calls after the application is setup,
   * but before the port is opened for business. That lets us do database migration and stairway
   * initialization on a system that is otherwise fully configured. The rule of thumb is that all
   * bean initialization should avoid database access. If there is additional database work to be
   * done, it should happen inside this method.
   */
  @Bean
  public SmartInitializingSingleton postSetupInitialization(ApplicationContext applicationContext) {
    return () -> {
      StartupInitializer.initialize(applicationContext);
    };
  }
}
