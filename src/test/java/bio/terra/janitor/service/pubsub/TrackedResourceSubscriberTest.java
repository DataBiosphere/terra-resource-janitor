package bio.terra.janitor.service.pubsub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.generated.model.*;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.common.exception.InvalidMessageException;
import bio.terra.janitor.db.TrackedResourceState;
import bio.terra.janitor.service.janitor.JanitorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
public class TrackedResourceSubscriberTest {
  @Autowired
  @Qualifier("objectMapper")
  private ObjectMapper objectMapper;

  @Autowired private JanitorService janitorService;

  @Autowired private MockMvc mvc;

  @Test
  public void receiveMessage() throws Exception {
    OffsetDateTime publishTime = OffsetDateTime.now(ZoneOffset.UTC);
    CloudResourceUid resource =
        new CloudResourceUid().googleBucketUid(new GoogleBucketUid().bucketName("bucket"));
    Map<String, String> labels = ImmutableMap.of("key1", "value1", "key2", "value2");
    ByteString data =
        ByteString.copyFromUtf8(
            objectMapper.writeValueAsString(
                new CreateResourceRequestBody()
                    .resourceUid(resource)
                    .creation(publishTime)
                    .expiration(publishTime)
                    .labels(labels)));
    AckReplyConsumer consumer =
        new AckReplyConsumer() {
          @Override
          public void ack() {}

          @Override
          public void nack() {}
        };

    TrackedResourceSubscriber.ResourceReceiver resourceReceiver =
        new TrackedResourceSubscriber.ResourceReceiver(objectMapper, janitorService);

    resourceReceiver.receiveMessage(PubsubMessage.newBuilder().setData(data).build(), consumer);

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
    assertEquals(labels, trackedResourceInfo.getLabels());
    assertEquals(TrackedResourceState.READY.toString(), trackedResourceInfo.getState());
  }

  @Test
  public void receiveMessage_invalid() throws Exception {
    AckReplyConsumer consumer =
        new AckReplyConsumer() {
          @Override
          public void ack() {
            Assertions.fail("Shouldn't ack for invalid message");
          }

          @Override
          public void nack() {}
        };

    TrackedResourceSubscriber.ResourceReceiver resourceReceiver =
        new TrackedResourceSubscriber.ResourceReceiver(new ObjectMapper(), null);
    Assertions.assertThrows(
        InvalidMessageException.class,
        () ->
            resourceReceiver.receiveMessage(
                PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8("bad json")).build(),
                consumer));
  }
}
