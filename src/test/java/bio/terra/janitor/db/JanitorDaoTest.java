package bio.terra.janitor.db;

import static bio.terra.janitor.app.common.TestUtils.*;
import static bio.terra.janitor.common.ResourceType.GOOGLE_PROJECT;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
public class JanitorDaoTest {
  @Autowired JanitorJdbcConfiguration jdbcConfiguration;
  @Autowired JanitorDao janitorDao;

  private NamedParameterJdbcTemplate jdbcTemplate;

  @BeforeEach
  public void setup() {
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  @Test
  public void createTrackedResource() throws Exception {
    CloudResourceUid cloudResourceUid = newGoogleProjectResourceUid();
    janitorDao.createResource(cloudResourceUid, DEFAULT_LABELS, CREATION, EXPIRATION);

    assertCreateResultMatch(
        cloudResourceUid,
        GOOGLE_PROJECT,
        Optional.of(CREATION),
        Optional.of(EXPIRATION),
        TIME_TO_LIVE_MINUTE,
        jdbcTemplate,
        DEFAULT_LABELS);
  }
}
