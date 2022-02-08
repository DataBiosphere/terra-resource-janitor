package bio.terra.janitor.integration;

import static bio.terra.janitor.app.configuration.BeanNames.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import bio.terra.janitor.generated.model.AzureDisk;
import bio.terra.janitor.generated.model.AzureNetwork;
import bio.terra.janitor.generated.model.AzureNetworkSecurityGroup;
import bio.terra.janitor.generated.model.AzurePublicIp;
import bio.terra.janitor.generated.model.AzureRelay;
import bio.terra.janitor.generated.model.AzureRelayHybridConnection;
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
import bio.terra.janitor.generated.model.TrackedResourceInfo;
import bio.terra.janitor.generated.model.TrackedResourceInfoList;
import bio.terra.janitor.integration.common.configuration.TestConfiguration;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.compute.models.KnownLinuxVirtualMachineImage;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.NetworkInterface;
import com.azure.resourcemanager.network.models.NetworkSecurityGroup;
import com.azure.resourcemanager.network.models.PublicIpAddress;
import com.azure.resourcemanager.relay.RelayManager;
import com.azure.resourcemanager.relay.models.HybridConnection;
import com.azure.resourcemanager.relay.models.RelayNamespace;
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
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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

  private Publisher publisher;

  private AIPlatformNotebooksCow notebooksCow;
  private StorageCow storageCow;
  private BigQueryCow bigQueryCow;
  private CloudResourceManagerCow resourceManagerCow;
  private String projectId;
  private ComputeManager computeManager;
  private RelayManager relayManager;

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
        newExpiredCreateResourceMessage(resource, OffsetDateTime.now(ZoneOffset.UTC))
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

  @Test
  public void subscribeAndCleanupResource_azurePublicIp() throws Exception {
    // Creates IP
    String ipName = randomNameWithUnderscore();
    PublicIpAddress createdIp =
        computeManager
            .networkManager()
            .publicIpAddresses()
            .define(ipName)
            .withRegion(Region.US_EAST)
            .withExistingResourceGroup(testConfiguration.getAzureManagedResourceGroupName())
            .withDynamicIP()
            .withTag("janitor.integration.test", "true")
            .create();

    // Verify resources are created in Azure
    assertEquals(
        ipName, computeManager.networkManager().publicIpAddresses().getById(createdIp.id()).name());

    CloudResourceUid ipUid =
        new CloudResourceUid()
            .azurePublicIp(
                new AzurePublicIp()
                    .ipName(ipName)
                    .resourceGroup(testConfiguration.getAzureResourceGroup()));

    // Publish a message to cleanup the IP.
    publishAndVerify(ipUid, ResourceState.DONE);

    // Resource is removed
    ManagementException ipDeleted =
        assertThrows(
            ManagementException.class,
            () -> computeManager.networkManager().publicIpAddresses().getById(createdIp.id()));
    assertEquals("ResourceNotFound", ipDeleted.getValue().getCode());
  }

  @Test
  public void subscribeAndCleanupResource_azureRelayAndHybridConnections() throws Exception {
    // Creates IP
    String relayName = randomNameWithUnderscore();
    RelayNamespace createdNameSpace =
        relayManager
            .namespaces()
            .define(relayName)
            .withRegion(Region.US_EAST)
            .withExistingResourceGroup(testConfiguration.getAzureManagedResourceGroupName())
            .create();

    // Verify resources are created in Azure
    assertEquals(relayName, relayManager.namespaces().getById(createdNameSpace.id()).name());

    String hybridConnectionName = randomNameWithUnderscore();
    HybridConnection createdHc =
        relayManager
            .hybridConnections()
            .define(hybridConnectionName)
            .withExistingNamespace(testConfiguration.getAzureManagedResourceGroupName(), relayName)
            .create();

    CloudResourceUid relayUid =
        new CloudResourceUid()
            .azureRelay(
                new AzureRelay()
                    .relayName(relayName)
                    .resourceGroup(testConfiguration.getAzureResourceGroup()));

    CloudResourceUid hcUid =
        new CloudResourceUid()
            .azureRelayHybridConnection(
                new AzureRelayHybridConnection()
                    .hybridConnectionName(hybridConnectionName)
                    .namespace(relayName)
                    .resourceGroup(testConfiguration.getAzureResourceGroup()));

    // Publish a message to cleanup the IP.
    publishAndVerify(hcUid, ResourceState.DONE);

    // Resource is removed
    ManagementException removeHc =
        assertThrows(
            ManagementException.class, () -> relayManager.namespaces().getById(createdHc.id()));
    assertEquals("ResourceNotFound", removeHc.getValue().getCode());

    // Publish a message to cleanup the IP.
    publishAndVerify(relayUid, ResourceState.DONE);

    // Resource is removed
    ManagementException removeRelay =
        assertThrows(
            ManagementException.class,
            () -> relayManager.namespaces().getById(createdNameSpace.id()));
    assertEquals("ResourceNotFound", removeRelay.getValue().getCode());
  }

  @Test
  public void subscribeAndCleanupResource_azureNetworkSecurityGroup() throws Exception {
    // Creates network security group
    String networkSgName = randomNameWithUnderscore();
    NetworkSecurityGroup createdNetworkSg =
        computeManager
            .networkManager()
            .networkSecurityGroups()
            .define(networkSgName)
            .withRegion(Region.US_EAST)
            .withExistingResourceGroup(testConfiguration.getAzureManagedResourceGroupName())
            .withTag("janitor.integration.test", "true")
            .create();

    // Verify resources are created in Azure
    assertEquals(
        networkSgName,
        computeManager
            .networkManager()
            .networkSecurityGroups()
            .getById(createdNetworkSg.id())
            .name());

    CloudResourceUid networkSgUid =
        new CloudResourceUid()
            .azureNetworkSecurityGroup(
                new AzureNetworkSecurityGroup()
                    .networkSecurityGroupName(networkSgName)
                    .resourceGroup(testConfiguration.getAzureResourceGroup()));

    // Publish a message to cleanup the network security group.
    publishAndVerify(networkSgUid, ResourceState.DONE);

    // Resource is removed
    ManagementException networkSgDeleted =
        assertThrows(
            ManagementException.class,
            () ->
                computeManager
                    .networkManager()
                    .networkSecurityGroups()
                    .getById(createdNetworkSg.id()));
    assertEquals("ResourceNotFound", networkSgDeleted.getValue().getCode());
  }

  @Test
  public void subscribeAndCleanupResource_azureNetwork() throws Exception {
    // Creates network
    String networkName = randomNameWithUnderscore();
    Network createdNetwork =
        computeManager
            .networkManager()
            .networks()
            .define(networkName)
            .withRegion(Region.US_EAST)
            .withExistingResourceGroup(testConfiguration.getAzureManagedResourceGroupName())
            .withTag("janitor.integration.test", "true")
            .withAddressSpace("10.0.0.0/16")
            .defineSubnet("mysubnet")
            .withAddressPrefix("10.0.0.0/24")
            .attach()
            .create();

    // Verify resources are created in Azure
    assertEquals(
        networkName,
        computeManager.networkManager().networks().getById(createdNetwork.id()).name());

    CloudResourceUid networkUid =
        new CloudResourceUid()
            .azureNetwork(
                new AzureNetwork()
                    .networkName(networkName)
                    .resourceGroup(testConfiguration.getAzureResourceGroup()));

    // Publish a message to cleanup the network.
    publishAndVerify(networkUid, ResourceState.DONE);

    // Resource is removed
    ManagementException networkDeleted =
        assertThrows(
            ManagementException.class,
            () -> computeManager.networkManager().networks().getById(createdNetwork.id()));
    assertEquals("ResourceNotFound", networkDeleted.getValue().getCode());
  }

  @Test
  public void subscribeAndCleanupResource_azureDisk() throws Exception {
    // Creates disk
    String diskName = randomNameWithUnderscore();
    Disk createdDisk =
        computeManager
            .disks()
            .define(diskName)
            .withRegion(Region.US_EAST)
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

  @Test
  public void subscribeAndCleanupResource_azureVirtualMachine() throws Exception {
    // Creates network
    String networkName = randomNameWithUnderscore();
    String subnetName = randomNameWithUnderscore();
    Network createdNetwork =
        computeManager
            .networkManager()
            .networks()
            .define(networkName)
            .withRegion(Region.US_EAST)
            .withExistingResourceGroup(testConfiguration.getAzureManagedResourceGroupName())
            .withTag("janitor.integration.test", "true")
            .withAddressSpace("10.0.0.0/16")
            .defineSubnet(subnetName)
            .withAddressPrefix("10.0.0.0/24")
            .attach()
            .create();

    // Creates disk
    String diskName = randomNameWithUnderscore();
    Disk createdDisk =
        computeManager
            .disks()
            .define(diskName)
            .withRegion(Region.US_EAST)
            .withExistingResourceGroup(testConfiguration.getAzureManagedResourceGroupName())
            .withData()
            .withSizeInGB(500)
            .withTag("janitor.integration.test", "true")
            .create();

    String nicName = randomNameWithUnderscore();
    NetworkInterface createdNetworkInterface =
        computeManager
            .networkManager()
            .networkInterfaces()
            .define(nicName)
            .withRegion(Region.US_EAST)
            .withExistingResourceGroup(testConfiguration.getAzureManagedResourceGroupName())
            .withExistingPrimaryNetwork(createdNetwork)
            .withSubnet(subnetName)
            .withPrimaryPrivateIPAddressDynamic()
            .withTag("janitor.integration.test", "true")
            .create();

    // Creates vm
    String vmName = randomNameWithUnderscore();
    VirtualMachine createdVm =
        computeManager
            .virtualMachines()
            .define(vmName)
            .withRegion(Region.US_EAST)
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
    assertEquals(
        networkName,
        computeManager.networkManager().networks().getById(createdNetwork.id()).name());

    assertEquals(diskName, computeManager.disks().getById(createdDisk.id()).name());

    assertEquals(vmName, computeManager.virtualMachines().getById(createdVm.id()).name());

    CloudResourceUid networkUid =
        new CloudResourceUid()
            .azureNetwork(
                new AzureNetwork()
                    .networkName(networkName)
                    .resourceGroup(testConfiguration.getAzureResourceGroup()));
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

    // Publish messages to cleanup the vm, network, and disk.
    publishAndVerify(vmUid, ResourceState.DONE);
    publishAndVerify(diskUid, ResourceState.DONE);
    publishAndVerify(networkUid, ResourceState.DONE);

    // All resources are removed
    ManagementException diskDeleted =
        assertThrows(
            ManagementException.class, () -> computeManager.disks().getById(createdDisk.id()));
    assertEquals("ResourceNotFound", diskDeleted.getValue().getCode());

    ManagementException networkDeleted =
        assertThrows(
            ManagementException.class,
            () -> computeManager.networkManager().networks().getById(createdNetwork.id()));
    assertEquals("ResourceNotFound", networkDeleted.getValue().getCode());

    ManagementException vmDeleted =
        assertThrows(
            ManagementException.class,
            () -> computeManager.virtualMachines().getById(createdVm.id()));
    assertEquals("ResourceNotFound", vmDeleted.getValue().getCode());
  }

  private void publishAndVerify(CloudResourceUid resource, ResourceState expectedState)
      throws Exception {
    OffsetDateTime publishTime = OffsetDateTime.now(ZoneOffset.UTC);
    publishAndVerify(newExpiredCreateResourceMessage(resource, publishTime), expectedState);
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
        pollUntilResourceState(request.getResourceUid(), expectedState, Duration.ofSeconds(5), 60);

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
      CloudResourceUid resource, OffsetDateTime now) {
    return new CreateResourceRequestBody()
        .resourceUid(resource)
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
          && trackedResourceInfoList.getResources().size() > 0
          && trackedResourceInfoList.getResources().get(0).getState().equals(expectedState)) {
        return trackedResourceInfoList;
      }
      ++numPolls;
    }
    throw new InterruptedException("Polling exceeded maxNumPolls");
  }
}
