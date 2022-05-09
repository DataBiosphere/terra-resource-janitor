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
  // Extracted from the adminUserList value.
  private Set<String> adminUsers;
  private boolean configBasedAuthzEnabled;
  private String testUserDomain;

  /**
   * The set of user emails authorized to call Janitor. Only used if {@code configBasedAuthzEnabled}
   * is true.
   */
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

  /**
   * Whether the config based authorization is enabled. If disabled, Janitor will not perform the
   * config authZ check, and all users will be authorized.
   */
  public boolean isConfigBasedAuthzEnabled() {
    return configBasedAuthzEnabled;
  }

  public void setConfigBasedAuthzEnabled(boolean configBasedAuthzEnabled) {
    this.configBasedAuthzEnabled = configBasedAuthzEnabled;
  }

  /**
   * Domain for test user emails. Janitor will only delegate credentials for users in this domain.
   */
  public String getTestUserDomain() {
    return testUserDomain;
  }

  public void setTestUserDomain(String testUserDomain) {
    this.testUserDomain = testUserDomain;
  }
}
