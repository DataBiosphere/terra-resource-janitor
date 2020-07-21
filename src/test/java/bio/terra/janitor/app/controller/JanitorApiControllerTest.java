package bio.terra.janitor.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.CreateResourceRequestBody;
import bio.terra.generated.model.CreatedResource;
import bio.terra.generated.model.GoogleProjectUid;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
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
  private static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value2");
  private static final int TIME_TO_LIVE_MINUTE = 100;

  @Autowired private MockMvc mvc;
  @Autowired JanitorJdbcConfiguration jdbcConfiguration;

  private NamedParameterJdbcTemplate jdbcTemplate;

  @BeforeEach
  public void setup() {
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  @Test
  public void createResourceSuccess() throws Exception {
    CreateResourceRequestBody body =
        new CreateResourceRequestBody()
            .resourceUid(
                new CloudResourceUid()
                    .googleProjectUid(
                        new GoogleProjectUid().projectId(UUID.randomUUID().toString())))
            .timeToLiveInMinutes(TIME_TO_LIVE_MINUTE)
            .labels(DEFAULT_LABELS);

    String response =
        this.mvc
            .perform(
                post("/api/janitor/v1/resource")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(body)))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertResourceExists(deserializeCreateResponse(response));
  }

  @Test
  public void createResourceFail_emptyCloudResourceUid() throws Exception {
    // Empty CloudResourceUid without any cloud resource specified.
    CloudResourceUid cloudResourceUid = new CloudResourceUid();
    CreateResourceRequestBody body =
        new CreateResourceRequestBody()
            .resourceUid(cloudResourceUid)
            .timeToLiveInMinutes(TIME_TO_LIVE_MINUTE)
            .labels(DEFAULT_LABELS);

    this.mvc
        .perform(
            post("/api/janitor/v1/resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(body)))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(status().isBadRequest());
  }

  private static CreatedResource deserializeCreateResponse(String jsonResponse)
      throws JsonProcessingException {
    return new ObjectMapper().readValue(jsonResponse, CreatedResource.class);
  }

  private void assertResourceExists(CreatedResource createdResource) {
    // TODO(yonghao): Use get endpoint once we have that.
    String sql = "SELECT count(*) from tracked_resource where id = :id";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", UUID.fromString(createdResource.getId()));
    assertEquals(1, jdbcTemplate.queryForMap(sql, params).size());
  }
}
