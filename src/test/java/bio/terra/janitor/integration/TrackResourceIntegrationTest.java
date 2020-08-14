package bio.terra.janitor.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.generated.model.*;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.TrackResourcePubsubConfiguration;
import bio.terra.janitor.db.TrackedResourceState;
import bio.terra.janitor.integration.common.configuration.IntegrationTestConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.pubsub.v1.Publisher;
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
  @Autowired private IntegrationTestConfiguration integrationTestConfiguration;
  @Autowired private MockMvc mvc;

  @Autowired
  @Qualifier("objectMapper")
  private ObjectMapper objectMapper;

  private Publisher publisher;

  private static final OffsetDateTime CREATION = OffsetDateTime.now(ZoneOffset.UTC);
  private static final OffsetDateTime EXPIRATION =
      OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);
  private static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value2");
  private static final CloudResourceUid RESOURCE_UID =
      new CloudResourceUid()
          .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
  private static final CreateResourceRequestBody TRACK_RESOURCE_MESSAGE =
      new CreateResourceRequestBody()
          .resourceUid(RESOURCE_UID)
          .creation(CREATION)
          .expiration(EXPIRATION)
          .labels(DEFAULT_LABELS);

  @BeforeEach
  public void setUp() throws Exception {
    TopicName topicName =
        TopicName.of(
            trackResourcePubsubConfiguration.getProjectId(),
            integrationTestConfiguration.getTrackResourceTopicId());
    publisher =
        Publisher.newBuilder(topicName)
            .setCredentialsProvider(
                FixedCredentialsProvider.create(
                    integrationTestConfiguration.getClientGoogleCredentialsOrDie()))
            .build();
  }

  @AfterEach
  public void tearDownPubsub() throws Exception {
    publisher.shutdown();
  }

  @Test
  public void subscribeTrackResource() throws Exception {
    ByteString data =
        ByteString.copyFromUtf8(objectMapper.writeValueAsString(TRACK_RESOURCE_MESSAGE));
    publisher.publish(PubsubMessage.newBuilder().setData(data).build());

    Thread.sleep(5000);

    String getResponse =
        this.mvc
            .perform(
                get("/api/janitor/v1/resource")
                    .queryParam("cloudResourceUid", objectMapper.writeValueAsString(RESOURCE_UID)))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    TrackedResourceInfoList resourceInfoList =
        objectMapper.readValue(getResponse, TrackedResourceInfoList.class);
    assertEquals(1, resourceInfoList.getResources().size());
    TrackedResourceInfo trackedResourceInfo = resourceInfoList.getResources().get(0);
    assertEquals(RESOURCE_UID, trackedResourceInfo.getResourceUid());
    assertEquals(CREATION, trackedResourceInfo.getCreation());
    assertEquals(EXPIRATION, trackedResourceInfo.getExpiration());
    assertEquals(TrackedResourceState.READY.name(), trackedResourceInfo.getState());
    assertEquals(DEFAULT_LABELS, trackedResourceInfo.getLabels());
  }
}
