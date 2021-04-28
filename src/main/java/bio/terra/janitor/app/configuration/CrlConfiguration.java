package bio.terra.janitor.app.configuration;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.api.services.common.Defaults;
import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.storage.StorageCow;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.CloudResourceManagerScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Configuration to use Terra Cloud Resource Library (CRL). */
@Component
public class CrlConfiguration {

  // Janitor only uses CRL Cows to delete resources. Cleanup is not needed.
  private final ClientConfig clientConfig =
      ClientConfig.Builder.newBuilder().setClient("terra-crl-janitor").build();

  @Bean
  @Lazy
  public AIPlatformNotebooksCow notebooksCow() throws IOException, GeneralSecurityException {
    return AIPlatformNotebooksCow.create(clientConfig, GoogleCredentials.getApplicationDefault());
  }

  @Bean
  @Lazy
  public BigQueryCow bigQueryCow() throws IOException, GeneralSecurityException {
    return BigQueryCow.create(clientConfig, GoogleCredentials.getApplicationDefault());
  }

  @Bean
  @Lazy
  public CloudResourceManagerCow cloudResourceManagerCow()
      throws IOException, GeneralSecurityException {
    return new CloudResourceManagerCow(
        clientConfig,
        new CloudResourceManager.Builder(
                Defaults.httpTransport(),
                Defaults.jsonFactory(),
                setHttpResourceManagerTimeout(
                    new HttpCredentialsAdapter(
                        GoogleCredentials.getApplicationDefault()
                            .createScoped(CloudResourceManagerScopes.all()))))
            .setApplicationName(clientConfig.getClientName()));
  }

  @Bean
  @Lazy
  public StorageCow storageCow() {
    return new StorageCow(clientConfig, StorageOptions.getDefaultInstance());
  }

  /** Sets longer timeout because ResourceManager operation may take longer than default timeout. */
  private static HttpRequestInitializer setHttpResourceManagerTimeout(
      final HttpRequestInitializer requestInitializer) {
    return httpRequest -> {
      requestInitializer.initialize(httpRequest);
      httpRequest.setConnectTimeout(3 * 60000); // 3 minutes connect timeout
      httpRequest.setReadTimeout(3 * 60000); // 3 minutes read timeout
    };
  }
}
