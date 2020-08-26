package bio.terra.janitor.app.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Component
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "iam")
public class IamConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(IamConfiguration.class);

  // The file path for admin user list.
  private String adminUserList;
  private Set<String> adminUsers;
  private boolean configBasedAuthZEnabled;

  public Set<String> getAdminUsers() {
    return adminUsers;
  }

  public void setAdminUserList(String adminUserList) {
    this.adminUserList = adminUserList;
    parseAdminUserFromConfig();
  }

  /** Reads {@code adminUserList} and convert to a {@code Set} */
  private void parseAdminUserFromConfig() {
    try {
      adminUsers =
          new ObjectMapper()
              .readValue(
                  adminUserList,
                  TypeFactory.defaultInstance().constructCollectionType(Set.class, String.class));
    } catch (IOException e) {
      logger.warn("Failed to read admin user file from configuration", e);
      throw new RuntimeException("Failed to read admin user file from configuration", e);
    }
  }

  public boolean isConfigBasedAuthZEnabled() {
    return configBasedAuthZEnabled;
  }

  public void setConfigBasedAuthZEnabled(boolean configBasedAuthZEnabled) {
    this.configBasedAuthZEnabled = configBasedAuthZEnabled;
  }
}
