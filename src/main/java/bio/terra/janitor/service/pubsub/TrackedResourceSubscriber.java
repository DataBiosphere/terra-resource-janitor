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
import com.google.common.annotations.VisibleForTesting;
import com.google.pubsub.v1.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Subscribes to tracked resource topic to update the Janitor database for new tracked resources.
 */
@Component
public class TrackedResourceSubscriber {
  private Logger logger = LoggerFactory.getLogger(TrackedResourceSubscriber.class);

  private final TrackResourcePubsubConfiguration trackResourcePubsubConfiguration;
  private final JanitorService janitorService;
  private final ObjectMapper objectMapper;

  @Autowired
  TrackedResourceSubscriber(
      TrackResourcePubsubConfiguration trackResourcePubsubConfiguration,
      JanitorService janitorService,
      @Qualifier("objectMapper") ObjectMapper objectMapper) {
    this.trackResourcePubsubConfiguration = trackResourcePubsubConfiguration;
    this.janitorService = janitorService;
    this.objectMapper = objectMapper;
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

    Subscriber subscriber =
        Subscriber.newBuilder(subscriptionName, new TestReceives(objectMapper, janitorService))
            .build();
    subscriber.startAsync().awaitRunning();
  }

  @VisibleForTesting
  static class TestReceives implements MessageReceiver {

    private final ObjectMapper objectMapper;
    private final JanitorService janitorService;

    TestReceives(ObjectMapper objectMapper, JanitorService janitorService) {
      this.objectMapper = objectMapper;
      this.janitorService = janitorService;
    }

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
      try {
        CreateResourceRequestBody body =
            objectMapper.readValue(
                message.getData().toStringUtf8(), CreateResourceRequestBody.class);
        janitorService.createResource(body);
        consumer.ack();
      } catch (JsonProcessingException e) {
        throw new InvalidMessageException(
            "Invalid track resource pubsub message: " + message.toString(), e);
      }
    }
  }
}
