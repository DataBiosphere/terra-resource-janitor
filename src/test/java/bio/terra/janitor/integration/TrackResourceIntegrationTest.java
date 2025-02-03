package bio.terra.janitor.integration;

import static bio.terra.janitor.app.configuration.BeanNames.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.janitor.app.configuration.CrlConfiguration;
import bio.terra.janitor.app.configuration.TrackResourcePubsubConfiguration;
import bio.terra.janitor.common.BaseIntegrationTest;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.generated.model.AzureBatchPool;
import bio.terra.janitor.generated.model.AzureDatabase;
import bio.terra.janitor.generated.model.AzureDisk;
import bio.terra.janitor.generated.model.AzureKubernetesNamespace;
import bio.terra.janitor.generated.model.AzureManagedIdentity;
import bio.terra.janitor.generated.model.AzureRelayHybridConnection;
import bio.terra.janitor.generated.model.AzureStorageContainer;
import bio.terra.janitor.generated.model.AzureVirtualMachine;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.janitor.generated.model.CreateResourceRequestBody;
import bio.terra.janitor.generated.model.GoogleAiNotebookInstanceUid;
import bio.terra.janitor.generated.model.GoogleBigQueryDatasetUid;
import bio.terra.janitor.generated.model.GoogleBigQueryTableUid;
import bio.terra.janitor.generated.model.GoogleBlobUid;
import bio.terra.janitor.generated.model.GoogleBucketUid;
import bio.terra.janitor.generated.model.GoogleProjectUid;
import bio.terra.janitor.generated.model.ResourceMetadata;
import bio.terra.janitor.generated.model.ResourceState;
import bio.terra.janitor.generated.model.TerraWorkspaceUid;
import bio.terra.janitor.generated.model.TrackedResourceInfo;
import bio.terra.janitor.generated.model.TrackedResourceInfoList;
import bio.terra.janitor.integration.common.configuration.TestConfiguration;
import bio.terra.janitor.service.cleanup.flight.KubernetesClientProvider;
import bio.terra.janitor.service.workspace.WorkspaceManagerService;
import bio.terra.workspace.client.ApiException;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.batch.BatchManager;
import com.azure.resourcemanager.batch.models.DeploymentConfiguration;
import com.azure.resourcemanager.batch.models.ImageReference;
import com.azure.resourcemanager.batch.models.Pool;
import com.azure.resourcemanager.batch.models.VirtualMachineConfiguration;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.compute.models.KnownLinuxVirtualMachineImage;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.models.Identity;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.NetworkInterface;
import com.azure.resourcemanager.postgresqlflexibleserver.PostgreSqlManager;
import com.azure.resourcemanager.postgresqlflexibleserver.models.Database;
import com.azure.resourcemanager.relay.RelayManager;
import com.azure.resourcemanager.relay.models.HybridConnection;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.BlobContainer;
import com.azure.resourcemanager.storage.models.PublicAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.bigquery.model.DatasetReference;
import com.google.api.services.bigquery.model.Table;
import com.google.api.services.bigquery.model.TableReference;
import com.google.api.services.cloudresourcemanager.v3.model.Operation;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.api.services.notebooks.v1.model.VmImage;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

@AutoConfigureMockMvc
public class TrackResourceIntegrationTest extends BaseIntegrationTest {

  @Autowired private TrackResourcePubsubConfiguration trackResourcePubsubConfiguration;
  @Autowired private TestConfiguration testConfiguration;
  @Autowired private MockMvc mvc;

  @Autowired
  @Qualifier(OBJECT_MAPPER)
  private ObjectMapper objectMapper;

  @Autowired private CrlConfiguration crlConfiguration;
  @Autowired private KubernetesClientProvider kubernetesClientProvider;

  private Publisher publisher;

  private AIPlatformNotebooksCow notebooksCow;
  private StorageCow storageCow;
  private BigQueryCow bigQueryCow;
  private CloudResourceManagerCow resourceManagerCow;
  private String projectId;
  private ComputeManager computeManager;
  private RelayManager relayManager;
  private MsiManager msiManager;
  private StorageManager storageManager;
  private PostgreSqlManager postgreSqlManager;
  private BatchManager batchManager;
  @MockBean private WorkspaceManagerService mockWorkspaceManagerService;

  private static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value2");

  private static final String CLAIM_EMAIL_KEY = "OIDC_CLAIM_email";
  private static final String CLAIM_SUBJECT_KEY = "OIDC_CLAIM_user_id";
  private static final String CLAIM_TOKEN_KEY = "OIDC_ACCESS_token";
  private static final String ADMIN_USER_EMAIL = "test1@email.com";
  private static final String ADMIN_SUBJECT_ID = "test1";
  private static final String ADMIN_TOKEN = "1234.ab-CD";

  @BeforeEach
  public void setUp() throws Exception {
    projectId = testConfiguration.getResourceProjectId();

    TopicName topicName =
        TopicName.of(
            trackResourcePubsubConfiguration.getProjectId(),
            testConfiguration.getTrackResourceTopicId());
    publisher =
        Publisher.newBuilder(topicName)
            .setCredentialsProvider(
                FixedCredentialsProvider.create(
                    testConfiguration.getClientGoogleCredentialsOrDie()))
            .build();

    notebooksCow =
        AIPlatformNotebooksCow.create(
            testConfiguration.createClientConfig(),
            testConfiguration.getResourceAccessGoogleCredentialsOrDie());
    storageCow =
        new StorageCow(
            testConfiguration.createClientConfig(),
            StorageOptions.newBuilder()
                .setCredentials(testConfiguration.getResourceAccessGoogleCredentialsOrDie())
                .setProjectId(projectId)
                .build());
    bigQueryCow =
        BigQueryCow.create(
            testConfiguration.createClientConfig(),
            testConfiguration.getResourceAccessGoogleCredentialsOrDie());
    resourceManagerCow =
        CloudResourceManagerCow.create(
            testConfiguration.createClientConfig(),
            testConfiguration.getResourceAccessGoogleCredentialsOrDie());

    computeManager =
        crlConfiguration.buildComputeManager(testConfiguration.getAzureResourceGroup());

    relayManager = crlConfiguration.buildRelayManager(testConfiguration.getAzureResourceGroup());

    msiManager = crlConfiguration.buildMsiManager(testConfiguration.getAzureResourceGroup());

    storageManager =
        crlConfiguration.buildStorageManager(testConfiguration.getAzureResourceGroup());

    postgreSqlManager =
        crlConfiguration.buildPostgreSqlManager(testConfiguration.getAzureResourceGroup());

    batchManager = crlConfiguration.buildBatchManager(testConfiguration.getAzureResourceGroup());
  }

  @AfterEach
  public void tearDownPubsub() throws Exception {
    publisher.shutdown();
  }

  @Test
  public void subscribeAndCleanupResource_googleBucket() throws Exception {
    // Creates bucket and verify.
    String bucketName = randomName();
    assertNull(storageCow.get(bucketName));
    BucketCow bucketCow = storageCow.create(BucketInfo.of(bucketName));
    BlobId blobId = BlobId.of(bucketCow.getBucketInfo().getName(), randomName());
    storageCow.create(BlobInfo.newBuilder(blobId).build());

    assertEquals(bucketName, storageCow.get(bucketName).getBucketInfo().getName());
    assertEquals(blobId.getName(), storageCow.get(blobId).getBlobInfo().getName());

    CloudResourceUid resource =
        new CloudResourceUid().googleBucketUid(new GoogleBucketUid().bucketName(bucketName));

    publishAndVerify(resource, ResourceState.DONE);

    // Resource is removed
    assertNull(storageCow.get(bucketName));
    assertNull(storageCow.get(blobId));
  }

  /** Try to let Janitor cleanup a Bucket that is already deleted in cloud. */
  @Test
  public void subscribeAndCleanupResource_alreadyDeletedBucket() throws Exception {
    // Creates bucket and verify.
    String bucketName = UUID.randomUUID().toString();
    assertNull(storageCow.get(bucketName));
    storageCow.create(BucketInfo.of(bucketName));
    assertEquals(bucketName, storageCow.get(bucketName).getBucketInfo().getName());
    storageCow.delete(bucketName);
    // Delete the resource.
    assertNull(storageCow.get(bucketName));

    CloudResourceUid resource =
        new CloudResourceUid().googleBucketUid(new GoogleBucketUid().bucketName(bucketName));

    publishAndVerify(resource, ResourceState.DONE);
  }

  @Test
  public void subscribeAndCleanupResource_googleBlob() throws Exception {
    // Creates Blob and verify.
    String bucketName = randomName();
    assertNull(storageCow.get(bucketName));
    BucketCow bucketCow = storageCow.create(BucketInfo.of(bucketName));
    BlobId blobId = BlobId.of(bucketCow.getBucketInfo().getName(), randomName());
    storageCow.create(BlobInfo.newBuilder(blobId).build());

    assertEquals(bucketName, storageCow.get(bucketName).getBucketInfo().getName());
    assertEquals(blobId.getName(), storageCow.get(blobId).getBlobInfo().getName());

    CloudResourceUid resource =
        new CloudResourceUid()
            .googleBlobUid(new GoogleBlobUid().bucketName(bucketName).blobName(blobId.getName()));

    publishAndVerify(resource, ResourceState.DONE);

    // Resource is removed
    assertNull(storageCow.get(blobId));
    storageCow.delete(bucketName);
  }

  /** Try to let Janitor cleanup a Blob that is already deleted in cloud. */
  @Test
  public void subscribeAndCleanupResource_alreadyDeletedBlob() throws Exception {
    // Creates Blob and verify.
    String bucketName = randomName();
    assertNull(storageCow.get(bucketName));
    BucketCow bucketCow = storageCow.create(BucketInfo.of(bucketName));
    BlobId blobId = BlobId.of(bucketCow.getBucketInfo().getName(), randomName());
    storageCow.create(BlobInfo.newBuilder(blobId).build());
    assertEquals(bucketName, storageCow.get(bucketName).getBucketInfo().getName());
    assertEquals(blobId.getName(), storageCow.get(blobId).getBlobInfo().getName());
    // Then delete this blob
    assertTrue(storageCow.delete(blobId));

    CloudResourceUid resource =
        new CloudResourceUid()
            .googleBlobUid(new GoogleBlobUid().bucketName(bucketName).blobName(blobId.getName()));

    publishAndVerify(resource, ResourceState.DONE);

    // Resource is removed
    assertNull(storageCow.get(blobId));
    storageCow.delete(bucketName);
  }

  @Test
  public void subscribeAndCleanupResource_googleDataset() throws Exception {
    // Creates dataset and table.
    String datasetName = randomNameWithUnderscore();
    String tableName = randomNameWithUnderscore();
    GoogleJsonResponseException datasetNotFoundException =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> bigQueryCow.datasets().get(projectId, datasetName).execute());
    assertEquals(HttpStatus.SC_NOT_FOUND, datasetNotFoundException.getStatusCode());
    DatasetReference datasetReference =
        new DatasetReference().setProjectId(projectId).setDatasetId(datasetName);
    Dataset datasetToCreate = new Dataset().setDatasetReference(datasetReference);
    bigQueryCow.datasets().insert(projectId, datasetToCreate).execute();
    TableReference tableReference =
        new TableReference()
            .setProjectId(projectId)
            .setDatasetId(datasetName)
            .setTableId(tableName);
    Table tableToCreate = new Table().setTableReference(tableReference);
    bigQueryCow.tables().insert(projectId, datasetName, tableToCreate).execute();

    // Verify resources are created in GCP
    assertEquals(
        datasetName,
        bigQueryCow
            .datasets()
            .get(projectId, datasetName)
            .execute()
            .getDatasetReference()
            .getDatasetId());
    assertEquals(
        tableName,
        bigQueryCow
            .tables()
            .get(projectId, datasetName, tableName)
            .execute()
            .getTableReference()
            .getTableId());

    CloudResourceUid datasetUid =
        new CloudResourceUid()
            .googleBigQueryDatasetUid(
                new GoogleBigQueryDatasetUid().projectId(projectId).datasetId(datasetName));

    // Publish a message to cleanup the dataset and make sure content inside is also deleted.
    publishAndVerify(datasetUid, ResourceState.DONE);

    // Resource is removed
    GoogleJsonResponseException datasetDeleted =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> bigQueryCow.datasets().get(projectId, datasetName).execute());
    assertEquals(HttpStatus.SC_NOT_FOUND, datasetDeleted.getStatusCode());
    GoogleJsonResponseException tableDeleted =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> bigQueryCow.tables().get(projectId, datasetName, tableName).execute());
    assertEquals(HttpStatus.SC_NOT_FOUND, tableDeleted.getStatusCode());

    // Try to publish another message to cleanup the same table and verify Janitor works fine for
    // tables already deleted by other flight.
    CloudResourceUid tableUid =
        new CloudResourceUid()
            .googleBigQueryTableUid(
                new GoogleBigQueryTableUid()
                    .projectId(projectId)
                    .datasetId(datasetName)
                    .tableId(tableName));
    publishAndVerify(tableUid, ResourceState.DONE);
  }

  @Test
  public void subscribeAndCleanupResource_googleBigQueryTable() throws Exception {
    // Creates dataset and table.
    String datasetName = randomNameWithUnderscore();
    String tableName = randomNameWithUnderscore();
    GoogleJsonResponseException datasetNotFoundException =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> bigQueryCow.datasets().get(projectId, datasetName).execute());
    assertEquals(HttpStatus.SC_NOT_FOUND, datasetNotFoundException.getStatusCode());
    DatasetReference datasetReference =
        new DatasetReference().setProjectId(projectId).setDatasetId(datasetName);
    Dataset datasetToCreate = new Dataset().setDatasetReference(datasetReference);
    bigQueryCow.datasets().insert(projectId, datasetToCreate).execute();
    TableReference tableReference =
        new TableReference()
            .setProjectId(projectId)
            .setDatasetId(datasetName)
            .setTableId(tableName);
    Table tableToCreate = new Table().setTableReference(tableReference);
    bigQueryCow.tables().insert(projectId, datasetName, tableToCreate).execute();

    // Verify resources are created in GCP
    assertEquals(
        datasetName,
        bigQueryCow
            .datasets()
            .get(projectId, datasetName)
            .execute()
            .getDatasetReference()
            .getDatasetId());
    assertEquals(
        tableName,
        bigQueryCow
            .tables()
            .get(projectId, datasetName, tableName)
            .execute()
            .getTableReference()
            .getTableId());

    CloudResourceUid tableUid =
        new CloudResourceUid()
            .googleBigQueryTableUid(
                new GoogleBigQueryTableUid()
                    .projectId(projectId)
                    .datasetId(datasetName)
                    .tableId(tableName));
    publishAndVerify(tableUid, ResourceState.DONE);

    // Resource is removed
    GoogleJsonResponseException tableDeleted =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> bigQueryCow.tables().get(projectId, datasetName, tableName).execute());
    assertEquals(HttpStatus.SC_NOT_FOUND, tableDeleted.getStatusCode());
    // Cleanup the dataset
    bigQueryCow.datasets().delete(projectId, datasetName).execute();
  }

  @Test
  public void subscribeAndCleanupResource_googleNotebookInstance() throws Exception {
    InstanceName instanceName =
        InstanceName.builder()
            .projectId(projectId)
            .location("us-west1-b")
            .instanceId(randomNotebookInstanceId())
            .build();
    createNotebookInstance(instanceName);

    CloudResourceUid notebookUid =
        new CloudResourceUid()
            .googleAiNotebookInstanceUid(
                new GoogleAiNotebookInstanceUid()
                    .projectId(instanceName.projectId())
                    .location(instanceName.location())
                    .instanceId(instanceName.instanceId()));
    publishAndVerify(notebookUid, ResourceState.DONE);

    GoogleJsonResponseException e =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> notebooksCow.instances().get(instanceName).execute());
    assertEquals(404, e.getStatusCode());
  }

  @Test
  public void subscribeAndCleanupResource_alreadyDeletedGoogleNotebookInstance() throws Exception {
    InstanceName instanceName =
        InstanceName.builder()
            .projectId(projectId)
            .location("us-west1-b")
            .instanceId(randomNotebookInstanceId())
            .build();
    createNotebookInstance(instanceName);
    assertEquals(
        instanceName.formatName(), notebooksCow.instances().get(instanceName).execute().getName());

    OperationCow<?> deleteOperation =
        notebooksCow
            .operations()
            .operationCow(notebooksCow.instances().delete(instanceName).execute());
    deleteOperation =
        OperationUtils.pollUntilComplete(
            deleteOperation, Duration.ofSeconds(10), Duration.ofMinutes(5));
    assertNull(deleteOperation.getOperationAdapter().getError());

    CloudResourceUid notebookUid =
        new CloudResourceUid()
            .googleAiNotebookInstanceUid(
                new GoogleAiNotebookInstanceUid()
                    .projectId(instanceName.projectId())
                    .location(instanceName.location())
                    .instanceId(instanceName.instanceId()));
    publishAndVerify(notebookUid, ResourceState.DONE);

    GoogleJsonResponseException e =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> notebooksCow.instances().get(instanceName).execute());
    assertEquals(404, e.getStatusCode());
  }

  @Test
  public void subscribeAndCleanupResource_googleProject() throws Exception {
    String projectId = randomProjectId();

    createProject(projectId);

    CloudResourceUid resource =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId(projectId));

    publishAndVerify(resource, ResourceState.DONE);

    // Project is ready for deletion
    Project project = resourceManagerCow.projects().get(projectId).execute();
    assertEquals("DELETE_REQUESTED", project.getState());
  }

  @Test
  public void subscribeAndCleanupResource_alreadyDeletedGoogleProject() throws Exception {
    String projectId = randomProjectId();
    createProject(projectId);

    CloudResourceUid resource =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId(projectId));

    publishAndVerify(resource, ResourceState.DONE);

    // Project is ready for deletion
    Project project = resourceManagerCow.projects().get(projectId).execute();
    assertEquals("DELETE_REQUESTED", project.getState());
  }

  @Test
  public void subscribeAndCleanupResource_neverCreatedGoogleProject_withMetadataOk()
      throws Exception {
    String projectId = randomProjectId();
    // Don't create the project.

    CloudResourceUid resource =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId(projectId));

    // If we know that the project should have been created where we have permissions to retrieve it
    // by metadata, we can successfully recognize that the project never existed.
    CreateResourceRequestBody request =
        newExpiredCreateResourceMessage(
                resource, JanitorDao.currentOffsetDateTime(), /*resourceMetadata=*/ null)
            .resourceMetadata(
                new ResourceMetadata()
                    .googleProjectParent(testConfiguration.getParentResourceId()));
    publishAndVerify(request, ResourceState.DONE);
  }

  @Test
  public void subscribeAndCleanupResource_neverCreatedGoogleProject_withoutMetadataError()
      throws Exception {
    String projectId = randomProjectId();
    // Don't create the project.

    CloudResourceUid resource =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId(projectId));

    // We can't tell if the project never existed or we don't have permissions to delete it.
    publishAndVerify(resource, ResourceState.ERROR);
  }

  @Disabled
  @Test
  public void subscribeAndCleanupResource_azureRelayHybridConnections() throws Exception {
    String hybridConnectionName = randomNameWithUnderscore();
    HybridConnection createdHc =
        relayManager
            .hybridConnections()
            .define(hybridConnectionName)
            .withExistingNamespace(
                testConfiguration.getAzureManagedResourceGroupName(),
                testConfiguration.getAzureRelayNamespace())
            .create();

    assertEquals(
        hybridConnectionName, relayManager.hybridConnections().getById(createdHc.id()).name());

    CloudResourceUid hcUid =
        new CloudResourceUid()
            .azureRelayHybridConnection(
                new AzureRelayHybridConnection()
                    .hybridConnectionName(hybridConnectionName)
                    .namespace(testConfiguration.getAzureRelayNamespace())
                    .resourceGroup(testConfiguration.getAzureResourceGroup()));

    // Publish a message to cleanup the hybrid connection.
    publishAndVerify(hcUid, ResourceState.DONE);

    // Resource is removed
    ManagementException removeHc =
        assertThrows(
            ManagementException.class,
            () -> relayManager.hybridConnections().getById(createdHc.id()));
    assertEquals("EntityNotFound", removeHc.getValue().getCode());
  }

  @Disabled
  @Test
  public void subscribeAndCleanupResource_azureDisk() throws Exception {
    // Creates disk
    String diskName = randomNameWithUnderscore();
    Disk createdDisk =
        computeManager
            .disks()
            .define(diskName)
            .withRegion(Region.US_SOUTH_CENTRAL)
            .withExistingResourceGroup(testConfiguration.getAzureManagedResourceGroupName())
            .withData()
            .withSizeInGB(500)
            .withTag("janitor.integration.test", "true")
            .create();

    // Verify resources are created in Azure
    assertEquals(diskName, computeManager.disks().getById(createdDisk.id()).name());

    CloudResourceUid diskUid =
        new CloudResourceUid()
            .azureDisk(
                new AzureDisk()
                    .diskName(diskName)
                    .resourceGroup(testConfiguration.getAzureResourceGroup()));

    // Publish a message to cleanup the network.
    publishAndVerify(diskUid, ResourceState.DONE);

    // Resource is removed
    ManagementException diskDeleted =
        assertThrows(
            ManagementException.class, () -> computeManager.disks().getById(createdDisk.id()));
    assertEquals("ResourceNotFound", diskDeleted.getValue().getCode());
  }

  @Disabled
  @Test
  public void subscribeAndCleanupResource_azureVirtualMachine() throws Exception {
    // Creates disk
    String diskName = randomNameWithUnderscore();
    Disk createdDisk =
        computeManager
            .disks()
            .define(diskName)
            .withRegion(Region.US_SOUTH_CENTRAL)
            .withExistingResourceGroup(testConfiguration.getAzureManagedResourceGroupName())
            .withData()
            .withSizeInGB(500)
            .withTag("janitor.integration.test", "true")
            .create();

    // Resolve network
    Network network =
        computeManager
            .networkManager()
            .networks()
            .getByResourceGroup(
                testConfiguration.getAzureManagedResourceGroupName(),
                testConfiguration.getAzureVnetName());

    // Create nic
    String nicName = randomNameWithUnderscore();
    NetworkInterface createdNetworkInterface =
        computeManager
            .networkManager()
            .networkInterfaces()
            .define(nicName)
            .withRegion(Region.US_SOUTH_CENTRAL)
            .withExistingResourceGroup(testConfiguration.getAzureManagedResourceGroupName())
            .withExistingPrimaryNetwork(network)
            .withSubnet("COMPUTE_SUBNET")
            .withPrimaryPrivateIPAddressDynamic()
            .withTag("janitor.integration.test", "true")
            .create();

    // Creates vm
    String vmName = randomNameWithUnderscore();
    VirtualMachine createdVm =
        computeManager
            .virtualMachines()
            .define(vmName)
            .withRegion(Region.US_SOUTH_CENTRAL)
            .withExistingResourceGroup(testConfiguration.getAzureManagedResourceGroupName())
            .withExistingPrimaryNetworkInterface(createdNetworkInterface)
            .withPopularLinuxImage(KnownLinuxVirtualMachineImage.CENTOS_8_3)
            .withRootUsername("crljanitor")
            .withRootPassword("cr!j4nitor")
            .withExistingDataDisk(createdDisk)
            .withSize(VirtualMachineSizeTypes.STANDARD_D11_V2)
            .withTag("janitor.integration.test", "true")
            .create();

    // Verify resources are created in Azure
    assertEquals(diskName, computeManager.disks().getById(createdDisk.id()).name());
    assertEquals(vmName, computeManager.virtualMachines().getById(createdVm.id()).name());

    CloudResourceUid diskUid =
        new CloudResourceUid()
            .azureDisk(
                new AzureDisk()
                    .diskName(diskName)
                    .resourceGroup(testConfiguration.getAzureResourceGroup()));
    CloudResourceUid vmUid =
        new CloudResourceUid()
            .azureVirtualMachine(
                new AzureVirtualMachine()
                    .vmName(vmName)
                    .resourceGroup(testConfiguration.getAzureResourceGroup()));

    // Publish messages to cleanup the vm and disk.
    publishAndVerify(vmUid, ResourceState.DONE);
    publishAndVerify(diskUid, ResourceState.DONE);

    // All resources are removed
    ManagementException diskDeleted =
        assertThrows(
            ManagementException.class, () -> computeManager.disks().getById(createdDisk.id()));
    assertEquals("ResourceNotFound", diskDeleted.getValue().getCode());

    ManagementException vmDeleted =
        assertThrows(
            ManagementException.class,
            () -> computeManager.virtualMachines().getById(createdVm.id()));
    assertEquals("ResourceNotFound", vmDeleted.getValue().getCode());
  }

  /** Clean up a fake WSM workspace. */
  @Disabled
  @Test
  public void subscribeAndCleanupResource_terraWorkspace() throws Exception {
    // Cleaning up workspaces relies on domain-wide delegation to impersonate test users. The tools
    // Janitor SA has this permission, but the test SAs do not, so we mock calls to WSM.
    // This test validates that Janitor handles requests to delete a workspace properly, but does
    // not validate that Janitor can impersonate users or actually delete workspaces.
    UUID fakeWorkspaceId = UUID.randomUUID();
    String fakeWorkspaceOwner = "fakeuseremail@test.firecloud.org";
    String fakeWsmInstanceId = "fakeinstance";

    CloudResourceUid resource =
        new CloudResourceUid()
            .terraWorkspace(
                new TerraWorkspaceUid()
                    .workspaceId(fakeWorkspaceId)
                    .workspaceManagerInstance(fakeWsmInstanceId));
    ResourceMetadata metadata = new ResourceMetadata().workspaceOwner(fakeWorkspaceOwner);
    publishAndVerify(resource, ResourceState.DONE, metadata);
  }

  /** Try to clean up an already deleted workspace, should succeed. */
  @Disabled
  @Test
  public void subscribeAndCleanupResource_alreadyDeletedTerraWorkspace() throws Exception {
    UUID fakeWorkspaceId = UUID.randomUUID();
    String fakeWorkspaceOwner = "fakeuseremail@test.firecloud.org";
    String fakeWsmInstanceId = "fakeinstance";
    ApiException workspaceAlreadyDeletedException =
        new ApiException(HttpStatus.SC_NOT_FOUND, "sorry, your workspace is in another castle");
    Mockito.doThrow(workspaceAlreadyDeletedException)
        .when(mockWorkspaceManagerService)
        .deleteWorkspace(any(), any(), any());

    CloudResourceUid resource =
        new CloudResourceUid()
            .terraWorkspace(
                new TerraWorkspaceUid()
                    .workspaceId(fakeWorkspaceId)
                    .workspaceManagerInstance(fakeWsmInstanceId));
    ResourceMetadata metadata = new ResourceMetadata().workspaceOwner(fakeWorkspaceOwner);
    // This should succeed despite the 404 response from mock WSM.
    publishAndVerify(resource, ResourceState.DONE, metadata);
  }

  @Disabled
  @Test
  public void subscribeAndCleanupResource_azureManagedIdentity() throws Exception {
    // Creates managed identity
    String identityName = randomNameWithUnderscore();
    Identity createdIdentity =
        msiManager
            .identities()
            .define(identityName)
            .withRegion(Region.US_SOUTH_CENTRAL)
            .withExistingResourceGroup(testConfiguration.getAzureManagedResourceGroupName())
            .withTag("janitor.integration.test", "true")
            .create();

    // Verify resources are created in Azure
    assertEquals(identityName, msiManager.identities().getById(createdIdentity.id()).name());

    CloudResourceUid identityUid =
        new CloudResourceUid()
            .azureManagedIdentity(
                new AzureManagedIdentity()
                    .identityName(identityName)
                    .resourceGroup(testConfiguration.getAzureResourceGroup()));

    // Publish a message to cleanup the managed identity.
    publishAndVerify(identityUid, ResourceState.DONE);

    // Resource is removed
    ManagementException identityDeleted =
        assertThrows(
            ManagementException.class, () -> msiManager.identities().getById(createdIdentity.id()));
    assertEquals("ResourceNotFound", identityDeleted.getValue().getCode());
  }

  @Disabled
  @Test
  public void subscribeAndCleanupResource_azureStorageContainer() throws Exception {
    String storageContainerName = randomName();

    // create storage container
    BlobContainer createdStorageContainer =
        storageManager
            .blobContainers()
            .defineContainer(storageContainerName)
            .withExistingStorageAccount(
                testConfiguration.getAzureManagedResourceGroupName(),
                testConfiguration.getAzureStorageAccountName())
            .withPublicAccess(PublicAccess.NONE)
            .create();

    // verify container is created in Azure
    assertEquals(
        storageContainerName,
        storageManager
            .blobContainers()
            .get(
                testConfiguration.getAzureManagedResourceGroupName(),
                testConfiguration.getAzureStorageAccountName(),
                createdStorageContainer.name())
            .name());

    // publish and verify cleanup of storage container by Janitor
    publishAndVerify(
        new CloudResourceUid()
            .azureStorageContainer(
                new AzureStorageContainer()
                    .storageContainerName(storageContainerName)
                    .storageAccountName(testConfiguration.getAzureStorageAccountName())
                    .resourceGroup(testConfiguration.getAzureResourceGroup())),
        ResourceState.DONE);

    // verify storage container is no longer present in Azure
    ManagementException removeStorageContainer =
        assertThrows(
            ManagementException.class,
            () ->
                storageManager
                    .blobContainers()
                    .get(
                        testConfiguration.getAzureManagedResourceGroupName(),
                        testConfiguration.getAzureStorageAccountName(),
                        createdStorageContainer.name()));
    assertEquals("ContainerNotFound", removeStorageContainer.getValue().getCode());
  }

  @Disabled
  @Test
  public void subscribeAndCleanupResource_azureDatabase() throws Exception {
    String databaseName = "janitortest" + System.currentTimeMillis();

    // create postgres database
    Database createdDatabase =
        postgreSqlManager
            .databases()
            .define(databaseName)
            .withExistingFlexibleServer(
                testConfiguration.getAzureManagedResourceGroupName(),
                testConfiguration.getAzurePostgresServerName())
            .create();

    // verify database is created in Azure
    assertEquals(
        databaseName,
        postgreSqlManager
            .databases()
            .get(
                testConfiguration.getAzureManagedResourceGroupName(),
                testConfiguration.getAzurePostgresServerName(),
                createdDatabase.name())
            .name());

    // publish and verify cleanup of the database by Janitor
    publishAndVerify(
        new CloudResourceUid()
            .azureDatabase(
                new AzureDatabase()
                    .databaseName(databaseName)
                    .serverName(testConfiguration.getAzurePostgresServerName())
                    .resourceGroup(testConfiguration.getAzureResourceGroup())),
        ResourceState.DONE);

    // verify database is no longer present in Azure
    ManagementException removeDatabase =
        assertThrows(
            ManagementException.class,
            () ->
                postgreSqlManager
                    .databases()
                    .get(
                        testConfiguration.getAzureManagedResourceGroupName(),
                        testConfiguration.getAzurePostgresServerName(),
                        createdDatabase.name()));
    assertEquals("ResourceNotFound", removeDatabase.getValue().getCode());
  }

  @Disabled
  @Test
  public void subscribeAndCleanupResource_azureKubernetesNamespace() throws Exception {
    String namespaceName = randomName();

    // create kubernetes namespace
    CoreV1Api coreApiClient =
        kubernetesClientProvider.createCoreApiClient(
            testConfiguration.getAzureResourceGroup(), testConfiguration.getAksClusterName());
    V1Namespace createdNamespace =
        coreApiClient.createNamespace(
            new V1Namespace().metadata(new V1ObjectMeta().name(namespaceName)),
            null,
            null,
            null,
            null);

    // verify namespace is created
    assertEquals(
        namespaceName, coreApiClient.readNamespace(namespaceName, null).getMetadata().getName());

    // publish and verify cleanup of the namespace by Janitor
    publishAndVerify(
        new CloudResourceUid()
            .azureKubernetesNamespace(
                new AzureKubernetesNamespace()
                    .namespaceName(namespaceName)
                    .clusterName(testConfiguration.getAksClusterName())
                    .resourceGroup(testConfiguration.getAzureResourceGroup())),
        ResourceState.DONE);

    // verify namespace is no longer present in Azure
    io.kubernetes.client.openapi.ApiException removeNamespace =
        assertThrows(
            io.kubernetes.client.openapi.ApiException.class,
            () -> coreApiClient.readNamespace(namespaceName, null));
    assertEquals(404, removeNamespace.getCode());
  }

  @Disabled
  @Test
  public void subscribeAndCleanupResource_azureBatchPool() throws Exception {
    String poolName = randomName();

    // create batch pool database
    Pool createdBatchPool =
        batchManager
            .pools()
            .define(poolName)
            .withExistingBatchAccount(
                testConfiguration.getAzureResourceGroup().getResourceGroupName(),
                testConfiguration.getAzureBatchAccountName())
            .withDeploymentConfiguration(
                new DeploymentConfiguration()
                    .withVirtualMachineConfiguration(
                        new VirtualMachineConfiguration()
                            .withImageReference(
                                new ImageReference()
                                    .withOffer("ubuntuserver")
                                    .withPublisher("canonical")
                                    .withSku("18.04-lts"))
                            .withNodeAgentSkuId("batch.node.ubuntu 18.04")))
            .withVmSize("Standard_D2s_v3")
            .create();

    // verify pool is created in Azure
    assertEquals(
        poolName,
        batchManager
            .pools()
            .get(
                testConfiguration.getAzureManagedResourceGroupName(),
                testConfiguration.getAzureBatchAccountName(),
                createdBatchPool.name())
            .name());

    // publish and verify cleanup of the pool by Janitor
    publishAndVerify(
        new CloudResourceUid()
            .azureBatchPool(
                new AzureBatchPool()
                    .id(createdBatchPool.id())
                    .batchAccountName(testConfiguration.getAzureBatchAccountName())
                    .resourceGroup(testConfiguration.getAzureResourceGroup())),
        ResourceState.DONE);

    // verify pool is no longer present in Azure
    ManagementException removePool =
        assertThrows(
            ManagementException.class,
            () ->
                batchManager
                    .pools()
                    .get(
                        testConfiguration.getAzureManagedResourceGroupName(),
                        testConfiguration.getAzureBatchAccountName(),
                        createdBatchPool.name()));
    assertEquals("PoolNotFound", removePool.getValue().getCode());
  }

  private void publishAndVerify(CloudResourceUid resource, ResourceState expectedState)
      throws Exception {
    publishAndVerify(resource, expectedState, null);
  }

  private void publishAndVerify(
      CloudResourceUid resource, ResourceState expectedState, ResourceMetadata resourceMetadata)
      throws Exception {
    OffsetDateTime publishTime = JanitorDao.currentOffsetDateTime();
    publishAndVerify(
        newExpiredCreateResourceMessage(resource, publishTime, resourceMetadata), expectedState);
  }

  /**
   * Publish message to Janitor to track resource and verify the resource reaches the expected state
   * by GET resource endpoint.
   */
  private void publishAndVerify(CreateResourceRequestBody request, ResourceState expectedState)
      throws Exception {
    ByteString data = ByteString.copyFromUtf8(objectMapper.writeValueAsString(request));

    publisher.publish(PubsubMessage.newBuilder().setData(data).build());

    TrackedResourceInfoList resourceInfoList =
        pollUntilResourceState(request.getResourceUid(), expectedState, Duration.ofSeconds(5), 120);

    assertEquals(1, resourceInfoList.getResources().size());
    TrackedResourceInfo trackedResourceInfo = resourceInfoList.getResources().get(0);
    assertEquals(request.getResourceUid(), trackedResourceInfo.getResourceUid());
    assertEquals(request.getCreation(), trackedResourceInfo.getCreation());
    assertEquals(request.getExpiration(), trackedResourceInfo.getExpiration());
    assertEquals(DEFAULT_LABELS, trackedResourceInfo.getLabels());
    assertEquals(expectedState, trackedResourceInfo.getState());
  }

  /** Returns a new {@link CreateResourceRequestBody} for a resource that is ready for cleanup. */
  private CreateResourceRequestBody newExpiredCreateResourceMessage(
      CloudResourceUid resource, OffsetDateTime now, ResourceMetadata metadata) {
    return new CreateResourceRequestBody()
        .resourceUid(resource)
        .resourceMetadata(metadata)
        .creation(now)
        .expiration(now)
        .labels(DEFAULT_LABELS);
  }

  private void createProject(String projectId) throws Exception {
    Operation operation =
        resourceManagerCow
            .projects()
            .create(
                new Project()
                    .setProjectId(projectId)
                    .setParent(testConfiguration.getParentResourceId()))
            .execute();
    OperationCow<Operation> operationCow = resourceManagerCow.operations().operationCow(operation);
    operationCow =
        OperationUtils.pollUntilComplete(
            operationCow, Duration.ofSeconds(5), Duration.ofSeconds(30));
    assertTrue(operationCow.getOperation().getDone());
    assertNull(operationCow.getOperation().getError());
  }

  /**
   * Creates a notebook instance for the {@link InstanceName}. Blocks until the instance is created
   * successfully or fails
   */
  private void createNotebookInstance(InstanceName instanceName)
      throws IOException, InterruptedException {
    OperationCow<com.google.api.services.notebooks.v1.model.Operation> operation =
        notebooksCow
            .operations()
            .operationCow(
                notebooksCow.instances().create(instanceName, defaultInstance()).execute());
    operation =
        OperationUtils.pollUntilComplete(operation, Duration.ofSeconds(30), Duration.ofMinutes(12));
    assertTrue(operation.getOperation().getDone());
    assertNull(operation.getOperation().getError());
  }

  /** Creates an {@link Instance} that's ready to be created. */
  private static Instance defaultInstance() {
    return new Instance()
        // A VM or Container image is required.
        .setVmImage(
            new VmImage().setProject("deeplearning-platform-release").setImageFamily("common-cpu"))
        // The machine type to used is required.
        .setMachineType("e2-standard-2");
  }

  /** Generates a random name to use for a cloud resource. */
  private static String randomName() {
    return UUID.randomUUID().toString();
  }

  /** Generates a random name to use for a cloud resource. */
  private static String randomRelayNameSpace() {
    return "a" + randomName().substring(0, 8) + "b";
  }

  /** Generates a random name to and replace '-' with '_'. */
  private static String randomNameWithUnderscore() {
    return UUID.randomUUID().toString().replace('-', '_');
  }

  /** Generates a random project id start with a letter and 30 characters long. */
  private static String randomProjectId() {
    // Project ids must starting with a letter and be no more than 30 characters long.
    return "p" + randomName().substring(0, 29);
  }

  /** Generates a random notebook instance id. */
  private static String randomNotebookInstanceId() {
    // Instance ids must start with a letter, be all lower case letters, numbers, and dashses.
    return "n" + randomName().toLowerCase();
  }

  /** Poll from get resource endpoint until it gets resources from Janitor in the expected state. */
  private TrackedResourceInfoList pollUntilResourceState(
      CloudResourceUid resource, ResourceState expectedState, Duration period, int maxNumPolls)
      throws Exception {
    TrackedResourceInfoList trackedResourceInfoList = null;
    int numPolls = 0;
    while (numPolls < maxNumPolls) {
      TimeUnit.MILLISECONDS.sleep(period.toMillis());
      String getResponse =
          this.mvc
              .perform(
                  get("/api/janitor/v1/resource")
                      .queryParam("cloudResourceUid", objectMapper.writeValueAsString(resource))
                      .header(CLAIM_EMAIL_KEY, ADMIN_USER_EMAIL)
                      .header(CLAIM_SUBJECT_KEY, ADMIN_SUBJECT_ID)
                      .header(CLAIM_TOKEN_KEY, ADMIN_TOKEN))
              .andDo(MockMvcResultHandlers.print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      trackedResourceInfoList = objectMapper.readValue(getResponse, TrackedResourceInfoList.class);
      if (trackedResourceInfoList != null
          && trackedResourceInfoList.getResources() != null
          && trackedResourceInfoList.getResources().size() > 0
          && trackedResourceInfoList.getResources().get(0).getState().equals(expectedState)) {
        return trackedResourceInfoList;
      }
      ++numPolls;
    }
    throw new InterruptedException("Polling exceeded maxNumPolls");
  }
}
