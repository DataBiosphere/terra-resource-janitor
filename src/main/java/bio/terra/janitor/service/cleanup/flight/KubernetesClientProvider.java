package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.generated.model.AzureResourceGroup;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class KubernetesClientProvider {
  private final CrlConfiguration crlConfiguration;

  @Autowired
  public KubernetesClientProvider(CrlConfiguration crlConfiguration) {
    this.crlConfiguration = crlConfiguration;
  }

  @NotNull
  public CoreV1Api createCoreApiClient(AzureResourceGroup resourceGroup, String clusterName) {
    KubeConfig kubeConfig = loadKubeConfig(resourceGroup, clusterName);
    var userToken = kubeConfig.getCredentials().get("token");

    ApiClient client =
        Config.fromToken(kubeConfig.getServer(), userToken)
            .setSslCaCert(
                new ByteArrayInputStream(
                    Base64.getDecoder()
                        .decode(
                            kubeConfig
                                .getCertificateAuthorityData()
                                .getBytes(StandardCharsets.UTF_8))));
    return new CoreV1Api(client);
  }

  @NotNull
  private KubeConfig loadKubeConfig(AzureResourceGroup resourceGroup, String clusterName) {
    var containerServiceManager = crlConfiguration.buildContainerServiceManager(resourceGroup);
    var rawKubeConfig =
        containerServiceManager
            .kubernetesClusters()
            .manager()
            .serviceClient()
            .getManagedClusters()
            .listClusterAdminCredentials(resourceGroup.getResourceGroupName(), clusterName)
            .kubeconfigs()
            .stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No kubeconfig found"));
    var kubeConfig =
        KubeConfig.loadKubeConfig(
            new InputStreamReader(
                new ByteArrayInputStream(rawKubeConfig.value()), StandardCharsets.UTF_8));
    return kubeConfig;
  }

  public Optional<RuntimeException> convertApiException(
      ApiException exception, HttpStatus... okStatuses) {
    var maybeStatusCode = Optional.ofNullable(HttpStatus.resolve(exception.getCode()));
    if (maybeStatusCode.isEmpty()) {
      return Optional.of(
          new RuntimeException("kubernetes api call failed without http status", exception));
    }
    var statusCode = maybeStatusCode.get();
    if (Arrays.asList(okStatuses).contains(statusCode)) {
      // do nothing, this is an ok status code
      return Optional.empty();
    } else if (statusCode.is5xxServerError()) {
      return Optional.of(new RetryException(exception));
    } else {
      return Optional.of(
          new RuntimeException(
              String.format("kubernetes api call failed: %s", exception.getResponseBody()),
              exception));
    }
  }

  public StepResult stepResultFromException(ApiException exception, HttpStatus... okStatuses) {
    var maybeException = convertApiException(exception, okStatuses);
    return maybeException
        .map(
            e -> {
              if (e instanceof RetryException) {
                return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
              } else {
                return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
              }
            })
        .orElseGet(StepResult::getStepResultSuccess);
  }
}
