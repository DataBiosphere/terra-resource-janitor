package bio.terra.janitor.integration;

import static bio.terra.janitor.app.configuration.BeanNames.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.*;
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
import bio.terra.janitor.app.configuration.TrackResourcePubsubConfiguration;
import bio.terra.janitor.common.BaseIntegrationTest;
import bio.terra.janitor.generated.model.*;
import bio.terra.janitor.integration.common.configuration.TestConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.services.cloudresourcemanager.model.Operation;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.cloudresourcemanager.model.ResourceId;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.api.services.notebooks.v1.model.VmImage;
import com.google.cloud.bigquery.*;
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
import org.junit.jupiter.api.*;
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

  private Publisher publisher;

  private AIPlatformNotebooksCow notebooksCow;
  private StorageCow storageCow;
  private BigQueryCow bigQueryCow;
  private CloudResourceManagerCow resourceManagerCow;
  private String projectId;
  private ResourceId parentResourceId;

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
        new BigQueryCow(
            testConfiguration.createClientConfig(),
            BigQueryOptions.newBuilder()
                .setCredentials(testConfiguration.getResourceAccessGoogleCredentialsOrDie())
                .setProjectId(projectId)
                .build());
    resourceManagerCow =
        CloudResourceManagerCow.create(
            testConfiguration.createClientConfig(),
            testConfiguration.getResourceAccessGoogleCredentialsOrDie());

    parentResourceId =
        new ResourceId().setType("folder").setId(testConfiguration.getParentResourceId());
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
    TableId tableId = TableId.of(datasetName, tableName);
    assertNull(bigQueryCow.getDataset(datasetName));
    bigQueryCow.create(DatasetInfo.newBuilder(datasetName).build());
    bigQueryCow.create(
        TableInfo.newBuilder(tableId, StandardTableDefinition.newBuilder().build()).build());

    // Verify resources are created in GCP
    assertEquals(
        datasetName,
        bigQueryCow.getDataset(datasetName).getDatasetInfo().getDatasetId().getDataset());
    assertEquals(tableName, bigQueryCow.getTable(tableId).getTableInfo().getTableId().getTable());

    CloudResourceUid datasetUid =
        new CloudResourceUid()
            .googleBigQueryDatasetUid(
                new GoogleBigQueryDatasetUid().projectId(projectId).datasetId(datasetName));

    // Publish a message to cleanup the dataset and make sure content inside is also deleted.
    publishAndVerify(datasetUid, ResourceState.DONE);

    // Resource is removed
    assertNull(bigQueryCow.getDataset(datasetName));
    assertNull(bigQueryCow.getTable(tableId));

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
    TableId tableId = TableId.of(datasetName, tableName);
    assertNull(bigQueryCow.getDataset(datasetName));
    bigQueryCow.create(DatasetInfo.newBuilder(datasetName).build());
    bigQueryCow.create(
        TableInfo.newBuilder(tableId, StandardTableDefinition.newBuilder().build()).build());

    // Verify resources are created in GCP
    assertEquals(
        datasetName,
        bigQueryCow.getDataset(datasetName).getDatasetInfo().getDatasetId().getDataset());
    assertEquals(tableName, bigQueryCow.getTable(tableId).getTableInfo().getTableId().getTable());

    CloudResourceUid tableUid =
        new CloudResourceUid()
            .googleBigQueryTableUid(
                new GoogleBigQueryTableUid()
                    .projectId(projectId)
                    .datasetId(datasetName)
                    .tableId(tableName));
    publishAndVerify(tableUid, ResourceState.DONE);

    // Resource is removed
    assertNull(bigQueryCow.getTable(tableId));
    // Cleanup the dataset
    assertTrue(bigQueryCow.delete(datasetName));
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
    assertEquals("DELETE_REQUESTED", project.getLifecycleState());
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
    assertEquals("DELETE_REQUESTED", project.getLifecycleState());
  }

  /**
   * Publish message to Janitor to track resource and verify the resource reaches the expected state
   * by GET resource endpoint.
   */
  private void publishAndVerify(CloudResourceUid resource, ResourceState expectedState)
      throws Exception {
    OffsetDateTime publishTime = OffsetDateTime.now(ZoneOffset.UTC);

    ByteString data =
        ByteString.copyFromUtf8(
            objectMapper.writeValueAsString(
                newExpiredCreateResourceMessage(resource, publishTime)));

    publisher.publish(PubsubMessage.newBuilder().setData(data).build());

    TrackedResourceInfoList resourceInfoList =
        pollUntilResourceState(resource, expectedState, Duration.ofSeconds(5), 10);

    assertEquals(1, resourceInfoList.getResources().size());
    TrackedResourceInfo trackedResourceInfo = resourceInfoList.getResources().get(0);
    assertEquals(resource, trackedResourceInfo.getResourceUid());
    assertEquals(publishTime, trackedResourceInfo.getCreation());
    assertEquals(publishTime, trackedResourceInfo.getExpiration());
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
            .create(new Project().setProjectId(projectId).setParent(parentResourceId))
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
