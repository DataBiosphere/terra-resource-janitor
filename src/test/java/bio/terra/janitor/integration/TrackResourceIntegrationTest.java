package bio.terra.janitor.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.generated.model.*;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.TrackResourcePubsubConfiguration;
import bio.terra.janitor.integration.common.configuration.TestConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
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
  @Qualifier("objectMapper")
  private ObjectMapper objectMapper;

  private Publisher publisher;

  private StorageCow storageCow;
  private static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value2");

  @BeforeEach
  public void setUp() throws Exception {
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
                .setProjectId(testConfiguration.getResourceProjectId())
                .build());
  }

  @AfterEach
  public void tearDownPubsub() throws Exception {
    publisher.shutdown();
  }

  @Test
  public void subscribeAndCleanupResource_googleBucket() throws Exception {
    // Creates bucket and verify.
    String bucketName = UUID.randomUUID().toString();
    assertNull(storageCow.get(bucketName));
    BucketCow createdBucket = storageCow.create(BucketInfo.of(bucketName));
    assertEquals(bucketName, createdBucket.getBucketInfo().getName());
    assertEquals(bucketName, storageCow.get(bucketName).getBucketInfo().getName());

    OffsetDateTime publishTime = OffsetDateTime.now(ZoneOffset.UTC);
    CloudResourceUid resource =
        new CloudResourceUid().googleBucketUid(new GoogleBucketUid().bucketName(bucketName));
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
                    .queryParam("cloudResourceUid", objectMapper.writeValueAsString(resource)))
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

    // Resource is removed
    assertNull(storageCow.get(bucketName));
  }

  /** Returns a new {@link CreateResourceRequestBody} that is ready for cleanup. */
  private CreateResourceRequestBody newExpiredCreateResourceMessage(
      CloudResourceUid resource, OffsetDateTime now) {
    return new CreateResourceRequestBody()
        .resourceUid(resource)
        .creation(now)
        .expiration(now)
        .labels(DEFAULT_LABELS);
  }
}
