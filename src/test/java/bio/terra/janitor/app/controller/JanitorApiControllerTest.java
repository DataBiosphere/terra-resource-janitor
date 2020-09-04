package bio.terra.janitor.app.controller;

import static bio.terra.janitor.app.configuration.BeanNames.OBJECT_MAPPER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.generated.model.*;
import bio.terra.janitor.app.Main;
import bio.terra.janitor.db.TrackedResourceState;
import bio.terra.janitor.service.iam.AuthHeaderKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
  private static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value2");
  private static final OffsetDateTime CREATION = OffsetDateTime.now(ZoneOffset.UTC);
  private static final OffsetDateTime EXPIRATION =
      OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);
  private static final String ADMIN_USER_EMAIL = "test1@email.com";

  @Autowired private MockMvc mvc;

  @Autowired
  @Qualifier(OBJECT_MAPPER)
  private ObjectMapper objectMapper;

  @Test
  public void createResourceSuccessGettable() throws Exception {
    CloudResourceUid resourceUid =
        new CloudResourceUid()
            .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
    CreateResourceRequestBody body =
        new CreateResourceRequestBody()
            .resourceUid(resourceUid)
            .creation(CREATION)
            .expiration(EXPIRATION)
            .labels(DEFAULT_LABELS);

    String createResponse =
        this.mvc
            .perform(
                post("/api/janitor/v1/resource")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AuthHeaderKeys.OIDC_CLAIM_EMAIL.getKeyName(), ADMIN_USER_EMAIL)
                    .content(objectMapper.writeValueAsString(body)))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    CreatedResource createdResource = objectMapper.readValue(createResponse, CreatedResource.class);

    String getResponse =
        this.mvc
            .perform(
                get(String.format("/api/janitor/v1/resource/%s", createdResource.getId()))
                    .header(AuthHeaderKeys.OIDC_CLAIM_EMAIL.getKeyName(), ADMIN_USER_EMAIL))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(createdResource.getId()))
            .andExpect(jsonPath("$.state").value(TrackedResourceState.READY.toString()))
            // Explicitly check that the string format date-time fields are strings since this is
            // easy to misconfigure with ObjectMappers.
            .andExpect(jsonPath("$.creation").isString())
            .andExpect(jsonPath("$.expiration").isString())
            .andReturn()
            .getResponse()
            .getContentAsString();
    TrackedResourceInfo trackedResourceInfo =
        objectMapper.readValue(getResponse, TrackedResourceInfo.class);
    assertEquals(resourceUid, trackedResourceInfo.getResourceUid());
    assertEquals(CREATION, trackedResourceInfo.getCreation());
    assertEquals(EXPIRATION, trackedResourceInfo.getExpiration());
    assertEquals(DEFAULT_LABELS, trackedResourceInfo.getLabels());
  }

  @Test
  public void createResourceFail_emptyCloudResourceUid() throws Exception {
    // Empty CloudResourceUid without any cloud resource specified.
    CloudResourceUid cloudResourceUid = new CloudResourceUid();
    CreateResourceRequestBody body =
        new CreateResourceRequestBody()
            .resourceUid(cloudResourceUid)
            .creation(CREATION)
            .expiration(EXPIRATION)
            .labels(DEFAULT_LABELS);

    this.mvc
        .perform(
            post("/api/janitor/v1/resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header(AuthHeaderKeys.OIDC_CLAIM_EMAIL.getKeyName(), ADMIN_USER_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(status().isBadRequest());
  }

  @Test
  public void createResource_notAuthorized() throws Exception {
    CloudResourceUid resourceUid =
        new CloudResourceUid()
            .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
    CreateResourceRequestBody body =
        new CreateResourceRequestBody()
            .resourceUid(resourceUid)
            .creation(CREATION)
            .expiration(EXPIRATION)
            .labels(DEFAULT_LABELS);

    this.mvc
        .perform(
            post("/api/janitor/v1/resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(status().isUnauthorized());
  }

  @Test
  public void getResource_notFound() throws Exception {
    this.mvc
        .perform(
            get(String.format("/api/janitor/v1/resource/not-a-real-id"))
                .header(AuthHeaderKeys.OIDC_CLAIM_EMAIL.getKeyName(), ADMIN_USER_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(status().isNotFound());
  }

  @Test
  public void getResources() throws Exception {
    CloudResourceUid resourceUid =
        new CloudResourceUid()
            .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
    CreateResourceRequestBody body1 =
        new CreateResourceRequestBody()
            .resourceUid(resourceUid)
            .creation(CREATION)
            .expiration(EXPIRATION)
            .labels(DEFAULT_LABELS);
    CreateResourceRequestBody body2 =
        new CreateResourceRequestBody()
            .resourceUid(resourceUid)
            .creation(CREATION)
            .expiration(EXPIRATION)
            .labels(DEFAULT_LABELS);

    String createResponse1 =
        this.mvc
            .perform(
                post("/api/janitor/v1/resource")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body1))
                    .header(AuthHeaderKeys.OIDC_CLAIM_EMAIL.getKeyName(), ADMIN_USER_EMAIL))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createResponse2 =
        this.mvc
            .perform(
                post("/api/janitor/v1/resource")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body2))
                    .header(AuthHeaderKeys.OIDC_CLAIM_EMAIL.getKeyName(), ADMIN_USER_EMAIL))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id1 = objectMapper.readValue(createResponse1, CreatedResource.class).getId();
    String id2 = objectMapper.readValue(createResponse2, CreatedResource.class).getId();

    String getResponse =
        this.mvc
            .perform(
                get("/api/janitor/v1/resource")
                    .queryParam("cloudResourceUid", objectMapper.writeValueAsString(resourceUid))
                    .header(AuthHeaderKeys.OIDC_CLAIM_EMAIL.getKeyName(), ADMIN_USER_EMAIL))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    TrackedResourceInfoList resourceInfoList =
        objectMapper.readValue(getResponse, TrackedResourceInfoList.class);
    assertThat(
        resourceInfoList.getResources().stream()
            .map(TrackedResourceInfo::getId)
            .collect(Collectors.toList()),
        Matchers.containsInAnyOrder(id1, id2));
  }
}
