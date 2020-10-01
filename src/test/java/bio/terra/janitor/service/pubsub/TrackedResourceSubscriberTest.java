package bio.terra.janitor.service.pubsub;

import static bio.terra.janitor.app.configuration.BeanNames.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.janitor.common.BaseUnitTest;
import bio.terra.janitor.common.exception.InvalidMessageException;
import bio.terra.janitor.db.*;
import bio.terra.janitor.generated.model.*;
import bio.terra.janitor.service.janitor.TrackedResourceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@AutoConfigureMockMvc
public class TrackedResourceSubscriberTest extends BaseUnitTest {
  @Autowired
  @Qualifier(OBJECT_MAPPER)
  private ObjectMapper objectMapper;

  @Autowired private TrackedResourceService trackedResourceService;
  @Autowired private JanitorDao janitorDao;

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
    List<TrackedResourceAndLabels> resources =
        janitorDao.retrieveResourcesAndLabels(
            TrackedResourceFilter.builder().cloudResourceUid(resource).build());

    assertEquals(1, resources.size());
    TrackedResourceAndLabels trackedResourceAndLabels = resources.get(0);
    TrackedResource trackedResource = trackedResourceAndLabels.trackedResource();
    assertEquals(resource, trackedResource.cloudResourceUid());
    assertEquals(publishTime.toInstant(), trackedResource.creation());
    assertEquals(publishTime.toInstant(), trackedResource.expiration());
    assertEquals(labels, trackedResourceAndLabels.labels());
    assertEquals(TrackedResourceState.READY, trackedResource.trackedResourceState());
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
