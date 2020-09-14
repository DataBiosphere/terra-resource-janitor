package bio.terra.janitor.integration;

import static bio.terra.janitor.app.configuration.BeanNames.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.generated.model.*;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.TrackResourcePubsubConfiguration;
import bio.terra.janitor.db.TrackedResourceState;
import bio.terra.janitor.integration.common.configuration.TestConfiguration;
import bio.terra.janitor.service.iam.AuthHeaderKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.services.cloudresourcemanager.model.Operation;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.cloudresourcemanager.model.ResourceId;
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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

@Tag("integration")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("classpath:application-integration-test.properties")
public class TrackResourceIntegrationTest {
  @Autowired private TrackResourcePubsubConfiguration trackResourcePubsubConfiguration;
  @Autowired private TestConfiguration testConfiguration;
  @Autowired private MockMvc mvc;

  @Autowired
  @Qualifier(OBJECT_MAPPER)
  private ObjectMapper objectMapper;

  private Publisher publisher;

  private StorageCow storageCow;
  private BigQueryCow bigQueryCow;
  private CloudResourceManagerCow resourceManagerCow;
  private String projectId;
  private ResourceId parentResourceId;

  private static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value2");

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

    publishAndVerifyResourceTracked(resource);

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

    publishAndVerifyResourceTracked(resource);
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

    publishAndVerifyResourceTracked(resource);

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

    publishAndVerifyResourceTracked(resource);

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
    publishAndVerifyResourceTracked(datasetUid);

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
    publishAndVerifyResourceTracked(tableUid);
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
    publishAndVerifyResourceTracked(tableUid);

    // Resource is removed
    assertNull(bigQueryCow.getTable(tableId));
    // Cleanup the dataset
    assertTrue(bigQueryCow.delete(datasetName));
  }

  @Test
  public void subscribeAndCleanupResource_googleProject() throws Exception {
    String projectId = randomProjectId();

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

    Project project = resourceManagerCow.projects().get(projectId).execute();
    assertEquals(projectId, project.getProjectId());
    assertEquals("ACTIVE", project.getLifecycleState());

    CloudResourceUid resource =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId(projectId));

    publishAndVerifyResourceTracked(resource);

    // Resource is removed
    project = resourceManagerCow.projects().get(projectId).execute();
    assertEquals("DELETE_REQUESTED", project.getLifecycleState());
  }

  @Test
  public void subscribeAndCleanupResource_alreadyDeletedGoogleProject() throws Exception {
    String projectId = randomProjectId();

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

    Project project = resourceManagerCow.projects().get(projectId).execute();
    assertEquals(projectId, project.getProjectId());
    assertEquals("ACTIVE", project.getLifecycleState());

    resourceManagerCow.projects().delete(projectId).execute();
    project = resourceManagerCow.projects().get(projectId).execute();
    assertEquals("DELETE_REQUESTED", project.getLifecycleState());

    CloudResourceUid resource =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId(projectId));

    publishAndVerifyResourceTracked(resource);

    // Resource is removed
    project = resourceManagerCow.projects().get(projectId).execute();
    assertEquals("DELETE_REQUESTED", project.getLifecycleState());
  }

  /**
   * Publish message to Janitor to track resource and verify the resource by GET resource endpoint.
   */
  private void publishAndVerifyResourceTracked(CloudResourceUid resource) throws Exception {
    OffsetDateTime publishTime = OffsetDateTime.now(ZoneOffset.UTC);

    ByteString data =
        ByteString.copyFromUtf8(
            objectMapper.writeValueAsString(
                newExpiredCreateResourceMessage(resource, publishTime)));

    publisher.publish(PubsubMessage.newBuilder().setData(data).build());

    // Sleep for 10 seconds
    Thread.sleep(10000);

    String getResponse =
        this.mvc
            .perform(
                get("/api/janitor/v1/resource")
                    .queryParam("cloudResourceUid", objectMapper.writeValueAsString(resource))
                    .header(AuthHeaderKeys.OIDC_CLAIM_EMAIL.getKeyName(), "test1@email.com"))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    TrackedResourceInfoList resourceInfoList =
        objectMapper.readValue(getResponse, TrackedResourceInfoList.class);
    assertEquals(1, resourceInfoList.getResources().size());
    TrackedResourceInfo trackedResourceInfo = resourceInfoList.getResources().get(0);
    assertEquals(resource, trackedResourceInfo.getResourceUid());
    assertEquals(publishTime, trackedResourceInfo.getCreation());
    assertEquals(publishTime, trackedResourceInfo.getExpiration());
    assertEquals(DEFAULT_LABELS, trackedResourceInfo.getLabels());
    assertEquals(TrackedResourceState.DONE.toString(), trackedResourceInfo.getState());
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

  /** Generates a random name to use for a cloud resource. */
  private static String randomName() {
    return UUID.randomUUID().toString();
  }

  /** Generates a random name to and replace '-' with '_'. */
  private static String randomNameWithUnderscore() {
    return UUID.randomUUID().toString().replace('-', '_');
  }

  /** Generates a random project id start with a letter and 30 characters long. */
  public static String randomProjectId() {
    // Project ids must starting with a letter and be no more than 30 characters long.
    return "p" + randomName().substring(0, 29);
  }
}
