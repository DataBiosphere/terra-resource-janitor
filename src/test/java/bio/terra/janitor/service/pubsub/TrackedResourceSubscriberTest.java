package bio.terra.janitor.service.pubsub;

import static bio.terra.janitor.app.configuration.BeanNames.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.generated.model.*;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.common.exception.InvalidMessageException;
import bio.terra.janitor.db.TrackedResourceState;
import bio.terra.janitor.service.iam.AuthenticatedUserRequest;
import bio.terra.janitor.service.janitor.TrackedResourceService;
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

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
public class TrackedResourceSubscriberTest {
  private static final AuthenticatedUserRequest ADMIN_USER =
      new AuthenticatedUserRequest().email("test1@email.com");

  @Autowired
  @Qualifier(OBJECT_MAPPER)
  private ObjectMapper objectMapper;

  @Autowired private TrackedResourceService trackedResourceService;

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
        new TrackedResourceSubscriber.ResourceReceiver(objectMapper, trackedResourceService);

    resourceReceiver.receiveMessage(PubsubMessage.newBuilder().setData(data).build(), consumer);
    trackedResourceService.getResources(resource);

    TrackedResourceInfoList resourceInfoList = trackedResourceService.getResources(resource);
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
