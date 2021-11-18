package bio.terra.janitor.app.configuration;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.api.services.common.Defaults;
import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.janitor.generated.model.AzureResourceGroup;
import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.compute.ComputeManager;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.cloudresourcemanager.v3.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.v3.CloudResourceManagerScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Configuration to use Terra Cloud Resource Library (CRL). */
@Component
public class CrlConfiguration {

  private final AzureConfiguration azureConfiguration;

  @Autowired
  public CrlConfiguration(AzureConfiguration azureConfiguration) {
    this.azureConfiguration = azureConfiguration;
  }

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

  public ComputeManager buildComputeManager(AzureResourceGroup azureResourceGroup) {
    TokenCredential azureCreds =
        new ClientSecretCredentialBuilder()
            .clientId(azureConfiguration.getManagedAppClientId())
            .clientSecret(azureConfiguration.getManagedAppClientSecret())
            .tenantId(azureConfiguration.getManagedAppTenantId())
            .build();

    AzureProfile azureProfile =
        new AzureProfile(
            azureResourceGroup.getTenantId(),
            azureResourceGroup.getSubscriptionId(),
            AzureEnvironment.AZURE);

    // We must use FQDN because there are two `Defaults` symbols imported otherwise.
    ComputeManager manager =
        bio.terra.cloudres.azure.resourcemanager.common.Defaults.crlConfigure(
                clientConfig, ComputeManager.configure())
            .authenticate(azureCreds, azureProfile);

    return manager;
  }
}
