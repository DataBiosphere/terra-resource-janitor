package bio.terra.janitor.integration;

import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.TrackResourcePubsubConfiguration;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.TopicName;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("integration")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@TestPropertySource("classpath:application-integration-test.properties")
public class TrackResourceIntegrationTest {
    @Autowired
    TrackResourcePubsubConfiguration trackResourcePubsubConfiguration;

    private Publisher publisher;

    @BeforeAll
    public void setupPubsub() throws Exception {
        TopicName topicName = TopicName.of(trackResourcePubsubConfiguration.getProjectId(), trackResourcePubsubConfiguration.getTopicId());
        publisher = Publisher.newBuilder(topicName).build();
    }

    @Test
    public void subscribeTrackResource() throws Exception {

    }
}
