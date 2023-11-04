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
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.relay.RelayManager;
import com.azure.resourcemanager.storage.StorageManager;
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
    return AIPlatformNotebooksCow.create(clientConfig, getApplicationDefaultCredentials());
  }

  @Bean
  @Lazy
  public BigQueryCow bigQueryCow() throws IOException, GeneralSecurityException {
    return BigQueryCow.create(clientConfig, getApplicationDefaultCredentials());
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
                        getApplicationDefaultCredentials()
                            .createScoped(CloudResourceManagerScopes.all()))))
            .setApplicationName(clientConfig.getClientName()));
  }

  @Bean
  @Lazy
  public StorageCow storageCow() {
    return new StorageCow(clientConfig, StorageOptions.getDefaultInstance());
  }

  /** Injection point for application-default credentials */
  public GoogleCredentials getApplicationDefaultCredentials() throws IOException {
    return GoogleCredentials.getApplicationDefault();
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

  /** Creates an Azure {@link ComputeManager} client for a given managed resource group. */
  public ComputeManager buildComputeManager(AzureResourceGroup resourceGroup) {
    // We must use FQDN because there are two `Defaults` symbols imported otherwise.
    return bio.terra.cloudres.azure.resourcemanager.common.Defaults.crlConfigure(
            clientConfig, ComputeManager.configure())
        .authenticate(getAzureCredential(), getAzureProfile(resourceGroup));
  }

  /** Creates an Azure {@link RelayManager} client for a given managed resource group. */
  public RelayManager buildRelayManager(AzureResourceGroup resourceGroup) {
    return bio.terra.cloudres.azure.resourcemanager.relay.Defaults.crlConfigure(
            clientConfig, RelayManager.configure())
        .authenticate(getAzureCredential(), getAzureProfile(resourceGroup));
  }

  /** Creates an Azure {@link MsiManager} client for a given managed resource group. */
  public MsiManager buildMsiManager(AzureResourceGroup resourceGroup) {
    return bio.terra.cloudres.azure.resourcemanager.common.Defaults.crlConfigure(
            clientConfig, MsiManager.configure())
        .authenticate(getAzureCredential(), getAzureProfile(resourceGroup));
  }

  /** Creates an Azure {@link StorageManager} client for a given managed resource group. */
  public StorageManager buildStorageManager(AzureResourceGroup resourceGroup) {
    return bio.terra.cloudres.azure.resourcemanager.common.Defaults.crlConfigure(
            clientConfig, StorageManager.configure())
        .authenticate(getAzureCredential(), getAzureProfile(resourceGroup));
  }

  private TokenCredential getAzureCredential() {
    return new ClientSecretCredentialBuilder()
        .clientId(azureConfiguration.getManagedAppClientId())
        .clientSecret(azureConfiguration.getManagedAppClientSecret())
        .tenantId(azureConfiguration.getManagedAppTenantId())
        .build();
  }

  private static AzureProfile getAzureProfile(AzureResourceGroup resourceGroup) {
    return new AzureProfile(
        resourceGroup.getTenantId(), resourceGroup.getSubscriptionId(), AzureEnvironment.AZURE);
  }
}
