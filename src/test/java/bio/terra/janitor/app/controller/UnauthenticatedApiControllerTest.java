package bio.terra.janitor.app.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.generated.model.SystemStatus;
import bio.terra.janitor.app.Main;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
public class UnauthenticatedApiControllerTest {
  @Autowired private MockMvc mvc;

  @Test
  public void testStatusOK() throws Exception {
    MockHttpServletResponse response =
        this.mvc.perform(get("/status")).andExpect(status().isOk()).andReturn().getResponse();

    SystemStatus status =
        new ObjectMapper().readValue(response.getContentAsString(), SystemStatus.class);
    assertTrue(status.isOk());
    assertThat(status.getSystems(), Matchers.hasKey("postgres"));
    assertThat(status.getSystems(), Matchers.hasKey("stairway-testubg"));
  }
}
