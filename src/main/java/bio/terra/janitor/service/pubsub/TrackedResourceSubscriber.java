package bio.terra.janitor.service.pubsub;

import static bio.terra.janitor.app.configuration.BeanNames.OBJECT_MAPPER;

import bio.terra.janitor.generated.model.CreateResourceRequestBody;
import bio.terra.janitor.app.configuration.TrackResourcePubsubConfiguration;
import bio.terra.janitor.common.exception.InvalidMessageException;
import bio.terra.janitor.service.janitor.TrackedResourceService;
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
  private static final Logger logger = LoggerFactory.getLogger(TrackedResourceSubscriber.class);

  private final TrackResourcePubsubConfiguration trackResourcePubsubConfiguration;
  private final TrackedResourceService trackedResourceService;
  private final ObjectMapper objectMapper;

  @Autowired
  TrackedResourceSubscriber(
      TrackResourcePubsubConfiguration trackResourcePubsubConfiguration,
      TrackedResourceService trackedResourceService,
      @Qualifier(OBJECT_MAPPER) ObjectMapper objectMapper) {
    this.trackResourcePubsubConfiguration = trackResourcePubsubConfiguration;
    this.trackedResourceService = trackedResourceService;
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
        Subscriber.newBuilder(
                subscriptionName, new ResourceReceiver(objectMapper, trackedResourceService))
            .build();
    subscriber.startAsync().awaitRunning();
  }

  @VisibleForTesting
  static class ResourceReceiver implements MessageReceiver {

    private final ObjectMapper objectMapper;
    private final TrackedResourceService trackedResourceService;

    ResourceReceiver(ObjectMapper objectMapper, TrackedResourceService trackedResourceService) {
      this.objectMapper = objectMapper;
      this.trackedResourceService = trackedResourceService;
    }

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
      try {
        CreateResourceRequestBody body =
            objectMapper.readValue(
                message.getData().toStringUtf8(), CreateResourceRequestBody.class);
        trackedResourceService.createResource(body);
        consumer.ack();
      } catch (Exception e) {
        logger.warn("Invalid track resource pubsub message: " + message.toString(), e);
        throw new InvalidMessageException(
            "Invalid track resource pubsub message: " + message.toString(), e);
      }
    }
  }
}
