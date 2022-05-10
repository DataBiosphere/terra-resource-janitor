package bio.terra.janitor.app.configuration;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "janitor.workspace-manager")
public class WorkspaceManagerConfiguration {

  // A map from instance IDs to Workspace Manager instance URLs, like:
  // "dev" -> "https://workspace.dsde-dev.broadinstitute.org"
  private List<String> instances;

  public List<String> getInstances() {
    return instances;
  }

  public void setInstances(List<String> instances) {
    this.instances = instances;
  }
}
