package bio.terra.janitor.service.pubsub;

import bio.terra.janitor.common.exception.InvalidMessageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
public class TrackedResourceSubscriberTest {
  @Test
  public void invalidMessage() throws Exception {
    AckReplyConsumer consumer =
        new AckReplyConsumer() {
          @Override
          public void ack() {
            Assertions.fail("Shouldn't ack for invalid message");
          }

          @Override
          public void nack() {}
        };

    TrackedResourceSubscriber.TestReceives testReceives =
        new TrackedResourceSubscriber.TestReceives(new ObjectMapper(), null);
    Assertions.assertThrows(
        InvalidMessageException.class,
        () ->
            testReceives.receiveMessage(
                PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8("bad json")).build(),
                consumer));
  }
}
