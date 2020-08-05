package bio.terra.janitor.service.pubsub;

import bio.terra.generated.model.CreateResourceRequestBody;
import bio.terra.janitor.app.configuration.TrackResourcePubsubConfiguration;
import bio.terra.janitor.common.exception.InvalidMessageException;
import bio.terra.janitor.service.janitor.JanitorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.*;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Subscribes to tracked resource topic to update the Janitor database for new tracked resources.
 */
@Component
public class TrackedResourceSubscriber {
  private Logger logger = LoggerFactory.getLogger(TrackedResourceSubscriber.class);

  private final TrackResourcePubsubConfiguration trackResourcePubsubConfiguration;
  private final JanitorService janitorService;

  @Autowired
  TrackedResourceSubscriber(
      TrackResourcePubsubConfiguration trackResourcePubsubConfiguration,
      JanitorService janitorService) {
    this.trackResourcePubsubConfiguration = trackResourcePubsubConfiguration;
    this.janitorService = janitorService;
  }

  public void initialize() {
    logger.info("Track resource pub/sub enabled: " + trackResourcePubsubConfiguration.isEnabled());

    if (!trackResourcePubsubConfiguration.isEnabled()) {
      return;
    }

    ProjectSubscriptionName subscriptionName =
        ProjectSubscriptionName.of(
            trackResourcePubsubConfiguration.getProjectId(),
            trackResourcePubsubConfiguration.getSubscription());

    // Instantiate an asynchronous message receiver.
    MessageReceiver receiver =
        (PubsubMessage message, AckReplyConsumer consumer) -> {
          // Handle incoming message, then always ack the received message.
          try {
            CreateResourceRequestBody body =
                new ObjectMapper()
                    .readValue(message.getData().toStringUtf8(), CreateResourceRequestBody.class);
            janitorService.createResourceWithCreation(
                body,
                Instant.ofEpochSecond(
                    message.getPublishTime().getSeconds(), message.getPublishTime().getNanos()));
          } catch (JsonProcessingException e) {
            throw new InvalidMessageException(
                "Invalid track resource pubsub message: " + message.toString(), e);
          } finally {
            // TODO(yonghao): Add dead letter queue to handle failed operations.
            consumer.ack();
          }
        };

    Subscriber subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
    subscriber.startAsync().awaitRunning();
  }
}
