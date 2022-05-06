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
@ConfigurationProperties(prefix = "janitor.iam")
public class IamConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(IamConfiguration.class);

  // The Janitor admin user list. This should be a formatted JSON list of strings, e.g.
  // '["testuser1@gmail.com"]'
  private String adminUserList;
  // Extracted from the adminUserList value. Mutually exclusive with adminUserList.
  private Set<String> adminUsers;
  // If the config based authorization are enabled. If disabled, Janitor will not perform the config
  // authZ check.
  private boolean configBasedAuthzEnabled;
  // Domain for test user emails. Janitor will only delegate credentials for users in this domain.
  private String testUserDomain;

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
      logger.warn("Failed to read admin user list from configuration", e);
      throw new RuntimeException("Failed to read admin user list from configuration", e);
    }
  }

  public boolean isConfigBasedAuthzEnabled() {
    return configBasedAuthzEnabled;
  }

  public void setConfigBasedAuthzEnabled(boolean configBasedAuthzEnabled) {
    this.configBasedAuthzEnabled = configBasedAuthzEnabled;
  }

  public String getTestUserDomain() {
    return testUserDomain;
  }

  public void setTestUserDomain(String testUserDomain) {
    this.testUserDomain = testUserDomain;
  }
}
