package bio.terra.janitor.service.cleanup.flight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.common.BaseUnitTest;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.generated.model.AzureKubernetesNamespace;
import bio.terra.janitor.generated.model.AzureResourceGroup;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.stairway.StepStatus;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class AzureKubernetesNamespaceCleanupStepTest extends BaseUnitTest {
  @Autowired private CrlConfiguration mockCrlConfiguration;
  @Autowired private JanitorDao mockJanitorDao;
  @Mock private KubernetesClientProvider mockKubernetesClientProvider;
  @Mock private CoreV1Api mockCoreV1Api;

  @Test
  void testCleanupSuccess() throws Exception {
    var resourceGroup =
        new AzureResourceGroup().tenantId("tenant").subscriptionId("sub").resourceGroupName("rg");
    var namespace =
        new AzureKubernetesNamespace()
            .resourceGroup(resourceGroup)
            .namespaceName("namespace")
            .clusterName("cluster");
    var resource = new CloudResourceUid().azureKubernetesNamespace(namespace);

    when(mockKubernetesClientProvider.createCoreApiClient(
            resourceGroup, namespace.getClusterName()))
        .thenReturn(mockCoreV1Api);
    when(mockCoreV1Api.readNamespace(namespace.getNamespaceName(), null))
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND.value(), "Not found"));

    var result =
        new AzureKubernetesNamespaceCleanupStep(
                mockCrlConfiguration, mockJanitorDao, mockKubernetesClientProvider)
            .cleanUp(resource, null);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));

    verify(mockKubernetesClientProvider)
        .createCoreApiClient(resourceGroup, namespace.getClusterName());
    verify(mockCoreV1Api).readNamespace(namespace.getNamespaceName(), null);
  }
}
