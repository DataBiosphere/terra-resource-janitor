package bio.terra.janitor.service.pubsub;

import bio.terra.janitor.app.configuration.TrackResourcePubsubConfiguration;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TrackedResourceSubscriber {
  private final TrackResourcePubsubConfiguration trackResourcePubsubConfiguration;

  @Autowired
  TrackedResourceSubscriber(TrackResourcePubsubConfiguration trackResourcePubsubConfiguration) {
    this.trackResourcePubsubConfiguration = trackResourcePubsubConfiguration;
  }

  public void initialize() {
    if (!trackResourcePubsubConfiguration.isPubsubEnabled()) {
      return;
    }

    System.out.println("~~~~~~~~~~~~");
    System.out.println("!!!!!!!!!!!!!!!!!!!!!");
    System.out.println(System.getenv());
    createTrackedResourceSubscriber(
        trackResourcePubsubConfiguration.getProjectId(),
        trackResourcePubsubConfiguration.getSubscription(),
        trackResourcePubsubConfiguration.getTopicId());
  }

  private void createTrackedResourceSubscriber(
      String projectId, String subscriptionId, String topicId) {
    ProjectSubscriptionName subscriptionName =
        ProjectSubscriptionName.of(projectId, subscriptionId);

    // Instantiate an asynchronous message receiver.
    MessageReceiver receiver =
        (PubsubMessage message, AckReplyConsumer consumer) -> {
          // Handle incoming message, then ack the received message.
          System.out.println("Id: " + message.getMessageId());
          System.out.println("Data: " + message.getData().toStringUtf8());
          consumer.ack();
        };

    Subscriber subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
    // Start the subscriber.
    subscriber.startAsync().awaitRunning();
    System.out.printf("Listening for messages on %s:\n", subscriptionName.toString());
  }
}
