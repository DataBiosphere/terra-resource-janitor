package bio.terra.janitor.db;

import static bio.terra.janitor.app.common.TestUtils.*;

import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.common.CloudResourceType;
import com.google.gson.Gson;
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
    // String jsonResourceUid = new Gson().toJson(DEFAULT_CLOUD_RESOURCE_UID);
    String jsonResourceUid = new Gson().toJson("cloudResourceUid");
    System.out.println("!!!!!!!!!!!!!33333333");
    System.out.println(jsonResourceUid);

    janitorDao.createResource(
        jsonResourceUid,
        CloudResourceType.Enum.GOOGLE_BLOB,
        DEFAULT_LABELS,
        DEFAULT_CREATION_TIME,
        DEFAULT_EXPIRATION_TIME);

    assertTrackedResourceMatch(
        jsonResourceUid,
        CloudResourceType.Enum.GOOGLE_BLOB,
        DEFAULT_CREATION_TIME,
        DEFAULT_EXPIRATION_TIME,
        jdbcTemplate);
    assertLabelMatch(jsonResourceUid, DEFAULT_LABELS, jdbcTemplate);
  }
}
