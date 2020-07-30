package bio.terra.janitor.service.pubsub;

import bio.terra.janitor.app.configuration.TrackResourcePubsubConfiguration;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.pubsub.v1.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    if(!trackResourcePubsubConfiguration.isPubsubEnabled()) {
      return;
    }

    System.out.println("~~~~~~~~~~~~");
    System.out.println("!!!!!!!!!!!!!!!!!!!!!");
    createTrackedResourceSubscriber(trackResourcePubsubConfiguration.getProjectId(), trackResourcePubsubConfiguration.getSubscription(), trackResourcePubsubConfiguration.getTopicId());
  }

  private void createTrackedResourceSubscriber(
      String projectId, String subscriptionId, String topicId) {
    ProjectSubscriptionName subscriptionName =
        ProjectSubscriptionName.of(projectId, subscriptionId);

    // Creates the subscription
    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
      TopicName topicName = TopicName.of(projectId, topicId);
      // Create a pull subscription with default acknowledgement deadline of 10 seconds.
      // Messages not successfully acknowledged within 10 seconds will get resent by the server.
      Subscription subscription =
          subscriptionAdminClient.createSubscription(
              subscriptionName, topicName, PushConfig.getDefaultInstance(), 10);
      System.out.println("Created pull subscription: " + subscription.getName());
    } catch (IOException e) {
      e.printStackTrace();
    }

    // Instantiate an asynchronous message receiver.
    MessageReceiver receiver =
        (PubsubMessage message, AckReplyConsumer consumer) -> {
          // Handle incoming message, then ack the received message.
          System.out.println("Id: " + message.getMessageId());
          System.out.println("Data: " + message.getData().toStringUtf8());
          consumer.ack();
        };

    Subscriber subscriber = null;
    try {
      subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
      // Start the subscriber.
      subscriber.startAsync().awaitRunning();
      System.out.printf("Listening for messages on %s:\n", subscriptionName.toString());
      // Allow the subscriber to run for 30s unless an unrecoverable error occurs.
      subscriber.awaitTerminated(30, TimeUnit.SECONDS);
    } catch (TimeoutException timeoutException) {
      // Shut down the subscriber after 30s. Stop receiving messages.
      subscriber.stopAsync();
    }
  }
}
