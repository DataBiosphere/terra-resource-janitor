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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TrackedResourceSubscriber {
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
    if (!trackResourcePubsubConfiguration.isPubsubEnabled()) {
      return;
    }

    createTrackedResourceSubscriber(
        trackResourcePubsubConfiguration.getProjectId(),
        trackResourcePubsubConfiguration.getSubscription());
  }

  private void createTrackedResourceSubscriber(String projectId, String subscriptionId) {
    ProjectSubscriptionName subscriptionName =
        ProjectSubscriptionName.of(projectId, subscriptionId);

    // Instantiate an asynchronous message receiver.
    MessageReceiver receiver =
        (PubsubMessage message, AckReplyConsumer consumer) -> {
          // Handle incoming message, then always ack the received message.
          try {
            CreateResourceRequestBody body =
                new ObjectMapper()
                    .readValue(message.getData().toStringUtf8(), CreateResourceRequestBody.class);
            janitorService.createResource(body);
          } catch (JsonProcessingException e) {
            throw new InvalidMessageException(
                "Invalid track resource pubsub message: " + message.toString(), e);
          } finally {
            consumer.ack();
          }
        };

    Subscriber subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
    // Start the subscriber.
    subscriber.startAsync().awaitRunning();
  }
}
