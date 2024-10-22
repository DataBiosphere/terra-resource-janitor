package bio.terra.janitor.service.pubsub;

import static bio.terra.janitor.app.configuration.BeanNames.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.janitor.common.BaseUnitTest;
import bio.terra.janitor.common.exception.InvalidMessageException;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.TrackedResource;
import bio.terra.janitor.db.TrackedResourceAndLabels;
import bio.terra.janitor.db.TrackedResourceFilter;
import bio.terra.janitor.db.TrackedResourceState;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.janitor.generated.model.CreateResourceRequestBody;
import bio.terra.janitor.generated.model.GoogleBucketUid;
import bio.terra.janitor.service.janitor.TrackedResourceService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonAppend;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.time.OffsetDateTime;
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
    OffsetDateTime publishTime = JanitorDao.currentOffsetDateTime();
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

  // See https://broadworkbench.atlassian.net/browse/CORE-104
  @Test
  public void receiveMessage_unknownFields() throws Exception {
    // Set up base bucket message
    OffsetDateTime publishTime = JanitorDao.currentOffsetDateTime();
    CloudResourceUid resource =
        new CloudResourceUid().googleBucketUid(new GoogleBucketUid().bucketName("bucket"));
    Map<String, String> labels = ImmutableMap.of("key1", "value1", "key2", "value2");

    // "Extend" the mapper for CloudResourceUid by adding a few extra fields to the JSON with null
    // values.
    @JsonAppend(
        attrs = {
          @JsonAppend.Attr(value = "azurePublicIp", include = JsonInclude.Include.ALWAYS),
          @JsonAppend.Attr(value = "unknownField", include = JsonInclude.Include.ALWAYS),
          @JsonAppend.Attr(value = "someOtherUnknownField", include = JsonInclude.Include.ALWAYS),
        })
    abstract class AzurePublicIpMixin {}
    objectMapper.addMixIn(CloudResourceUid.class, AzurePublicIpMixin.class);
    ByteString data =
        ByteString.copyFromUtf8(
            objectMapper.writeValueAsString(
                new CreateResourceRequestBody()
                    .resourceUid(resource)
                    .creation(publishTime)
                    .expiration(publishTime)
                    .labels(labels)));

    // Send the extended JSON to the TrackedResourceSubscriber
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

    // The bucket message should have been processed
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
}
