package bio.terra.janitor.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.CreateResourceRequestBody;
import bio.terra.generated.model.GoogleProjectUid;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.TrackResourcePubsubConfiguration;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.TrackedResource;
import bio.terra.janitor.db.TrackedResourceState;
import bio.terra.janitor.integration.common.configuration.TestConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("integration")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("classpath:application-integration-test.properties")
public class TrackResourceIntegrationTest {
  @Autowired private TrackResourcePubsubConfiguration trackResourcePubsubConfiguration;
  @Autowired private TestConfiguration testConfiguration;
  @Autowired private JanitorDao janitorDao;

  private static Publisher publisher;

  /** Google Service Account path used to publish message. */
  private static String CLIENT_SERVICE_ACCOUNT_PATH = "rendered/client-sa-account.json";

  private static final int TIME_TO_LIVE_MINUTE = 5;
  private static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value222222222222");
  private static final CloudResourceUid RESOURCE_UID =
      new CloudResourceUid()
          .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
  private static final CreateResourceRequestBody TRACK_RESOURCE_MESSAGE =
      new CreateResourceRequestBody()
          .resourceUid(RESOURCE_UID)
          .timeToLiveInMinutes(TIME_TO_LIVE_MINUTE)
          .labels(DEFAULT_LABELS);

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
                    getGoogleCredentialsOrDie(CLIENT_SERVICE_ACCOUNT_PATH)))
            .build();
  }

  @AfterEach
  public void tearDownPubsub() throws Exception {
    publisher.shutdown();
  }

  @Test
  public void subscribeTrackResource() throws Exception {
    ByteString data =
        ByteString.copyFromUtf8(new ObjectMapper().writeValueAsString(TRACK_RESOURCE_MESSAGE));
    publisher.publish(PubsubMessage.newBuilder().setData(data).build());
    Thread.sleep(5000);

    TrackedResource trackedResourceInfo = janitorDao.retrieveTrackedResource(RESOURCE_UID).get();

    assertEquals(RESOURCE_UID, trackedResourceInfo.cloudResourceUid());
    assertEquals(
        Duration.ofMinutes(TIME_TO_LIVE_MINUTE),
        Duration.between(trackedResourceInfo.creation(), trackedResourceInfo.expiration()));
    assertEquals(TrackedResourceState.READY, trackedResourceInfo.trackedResourceState());
  }

  private static ServiceAccountCredentials getGoogleCredentialsOrDie(String serviceAccountPath) {
    try {
      return ServiceAccountCredentials.fromStream(
          Thread.currentThread().getContextClassLoader().getResourceAsStream(serviceAccountPath));
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to load GoogleCredentials from " + serviceAccountPath + "\n", e);
    }
  }
}
