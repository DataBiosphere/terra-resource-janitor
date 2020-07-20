package bio.terra.janitor.app.controller;

import static bio.terra.janitor.app.common.TestUtils.*;
import static bio.terra.janitor.app.common.TestUtils.DEFAULT_LABELS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.CreatedResource;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
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
  public void createResourceSuccess() throws Exception {
    String response =
        this.mvc
            .perform(
                post("/api/janitor/v1/resource")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        newJsonCreateRequestBody(
                            newGoogleProjectResourceUid(), Optional.of(DEFAULT_LABELS))))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertResourceExists(response);
  }

  @Test
  public void createResourceFail_missingField() throws Exception {
    this.mvc
        .perform(
            post("/api/janitor/v1/resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content(newJsonCreateRequestBody(new CloudResourceUid(), Optional.empty())))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(status().is4xxClientError());
  }

  private void assertResourceExists(String jsonResponse) throws JsonProcessingException {
    // TODO(yonghao): Use get endpoint once we have that.
    String sql = "SELECT count(*) from tracked_resource where id = :id";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue(
                "id",
                UUID.fromString(
                    new ObjectMapper().readValue(jsonResponse, CreatedResource.class).getId()));
    assertEquals(1, jdbcTemplate.queryForMap(sql, params).size());
  }
}
