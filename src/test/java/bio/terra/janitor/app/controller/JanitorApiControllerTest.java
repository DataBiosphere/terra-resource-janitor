package bio.terra.janitor.app.controller;

import static bio.terra.janitor.app.common.TestUtils.*;
import static bio.terra.janitor.app.common.TestUtils.DEFAULT_LABELS;
import static bio.terra.janitor.common.JanitorResourceTypeEnum.GOOGLE_PROJECT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
public class JanitorApiControllerTest {
  @Autowired private MockMvc mvc;
  @Autowired JanitorJdbcConfiguration jdbcConfiguration;

  private NamedParameterJdbcTemplate jdbcTemplate;

  @BeforeEach
  public void setup() {
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  @Test
  public void testCreateResourceSuccess() throws Exception {
    CloudResourceUid cloudResourceUid = newGoogleProjectResourceUid();
    this.mvc
        .perform(
            post("/api/janitor/v1/resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content(newJsonCreateRequestBody(cloudResourceUid, Optional.of(DEFAULT_LABELS))))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(status().isOk());

    // Verify data are created correctly.
    assertCreateResultMatch(
        new ObjectMapper().writeValueAsString(cloudResourceUid),
        GOOGLE_PROJECT,
        CREATION,
        EXPIRATION,
        jdbcTemplate,
        DEFAULT_LABELS);
  }

  @Test
  public void testCreateResourceFail_missingField() throws Exception {
    this.mvc
        .perform(
            post("/api/janitor/v1/resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content(newJsonCreateRequestBody(new CloudResourceUid(), Optional.empty())))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(status().is4xxClientError());
  }
}
