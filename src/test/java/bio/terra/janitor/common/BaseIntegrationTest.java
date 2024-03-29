package bio.terra.janitor.common;

import bio.terra.janitor.app.Main;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("integration")
@ActiveProfiles({"test", "integration"})
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Main.class)
public class BaseIntegrationTest {}
