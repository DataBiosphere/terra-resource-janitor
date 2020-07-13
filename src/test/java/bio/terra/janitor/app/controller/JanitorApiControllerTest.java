package bio.terra.janitor.app.controller;

import static bio.terra.janitor.app.common.TestUtils.defaultJsonCreateRequestBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import bio.terra.janitor.app.Main;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

  @Test
  public void testCreateResource_success() throws Exception {
    assertEquals(
        "{\"ok\":true,\"systems\":{\"postgres\":{\"ok\":true,\"messages\":null}}}",
        this.mvc
            .perform(
                post("/api/janitor/v1/resource")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(defaultJsonCreateRequestBody()))
            .andDo(MockMvcResultHandlers.print())
            // .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getErrorMessage());
    // .getContentAsString());
  }
}
