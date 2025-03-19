package bio.terra.janitor.service.cleanup.flight;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.common.utils.RetryUtils;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.ResourceMetadata;
import bio.terra.janitor.generated.model.AzureKubernetesNamespace;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1NamespaceStatus;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** Step to clean up an Azure Kubernetes namespace. */
public class AzureKubernetesNamespaceCleanupStep extends ResourceCleanupStep {
  private static final Logger logger =
      LoggerFactory.getLogger(AzureKubernetesNamespaceCleanupStep.class);
  private final CrlConfiguration crlConfiguration;
  private final KubernetesClientProvider kubernetesClientProvider;

  public AzureKubernetesNamespaceCleanupStep(
      CrlConfiguration crlConfiguration,
      JanitorDao janitorDao,
      KubernetesClientProvider kubernetesClientProvider) {
    super(janitorDao);
    this.crlConfiguration = crlConfiguration;
    this.kubernetesClientProvider = kubernetesClientProvider;
  }

  @Override
  protected StepResult cleanUp(CloudResourceUid resourceUid, ResourceMetadata metadata)
      throws InterruptedException, RetryException {
    AzureKubernetesNamespace namespace = resourceUid.getAzureKubernetesNamespace();

    var api =
        kubernetesClientProvider.createCoreApiClient(
            namespace.getResourceGroup(), namespace.getClusterName());

    try {
      logger.info("Deleting namespace {}", namespace);
      api.deleteNamespace(namespace.getNamespaceName()).execute();
    } catch (ApiException e) {
      return kubernetesClientProvider.stepResultFromException(e, HttpStatus.NOT_FOUND);
    }

    waitForDeletion(api, namespace.getNamespaceName());

    return StepResult.getStepResultSuccess();
  }

  private void waitForDeletion(CoreV1Api coreApiClient, String namespsaceName)
      throws InterruptedException {
    try {
      RetryUtils.getWithRetry(
          this::isNamespaceGone,
          () -> getNamespaceStatus(coreApiClient, namespsaceName),
          Duration.ofMinutes(10),
          Duration.ofSeconds(10),
          0.0,
          Duration.ofSeconds(10));
    } catch (InterruptedException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(
          String.format("Error waiting for namespace %s to be deleted", namespsaceName), e);
    }
  }

  private Optional<String> getNamespaceStatus(CoreV1Api coreApiClient, String namespaceName) {
    try {
      var namespace = coreApiClient.readNamespaceStatus(namespaceName).execute();
      var phase = Optional.ofNullable(namespace.getStatus()).map(V1NamespaceStatus::getPhase);
      logger.info("Status = {} for azure namespace = {}", phase, namespaceName);
      return phase;
    } catch (ApiException e) {
      if (e.getCode() == HttpStatus.NOT_FOUND.value()) {
        return Optional.empty();
      } else {
        // this is called in a retry loop, so we don't want to throw an exception here
        logger.error("Error reading namespace {}", namespaceName, e);
        return Optional.of("Error checking namespace status");
      }
    }
  }

  private boolean isNamespaceGone(Optional<String> namespacePhase) {
    return namespacePhase.isEmpty();
  }
}
