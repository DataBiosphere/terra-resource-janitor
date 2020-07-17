package bio.terra.janitor.db;

import static bio.terra.janitor.app.common.TestUtils.*;
import static bio.terra.janitor.common.ResourceType.GOOGLE_PROJECT;

import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    String cloudResourceUid = new ObjectMapper().writeValueAsString(newGoogleProjectResourceUid());
    janitorDao.createResource(
        cloudResourceUid, GOOGLE_PROJECT, DEFAULT_LABELS, CREATION, EXPIRATION);

    assertCreateResultMatch(
        cloudResourceUid, GOOGLE_PROJECT, CREATION, EXPIRATION, jdbcTemplate, DEFAULT_LABELS);
  }
}
