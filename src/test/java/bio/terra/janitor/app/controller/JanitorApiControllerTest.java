package bio.terra.janitor.app.controller;

import static bio.terra.janitor.app.configuration.BeanNames.OBJECT_MAPPER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.janitor.app.Main;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.TrackedResourceState;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.janitor.generated.model.CreateResourceRequestBody;
import bio.terra.janitor.generated.model.CreatedResource;
import bio.terra.janitor.generated.model.GoogleProjectUid;
import bio.terra.janitor.generated.model.ResourceMetadata;
import bio.terra.janitor.generated.model.ResourceState;
import bio.terra.janitor.generated.model.TrackedResourceInfo;
import bio.terra.janitor.generated.model.TrackedResourceInfoList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

@Tag("unit")
@ActiveProfiles({"test", "unit"})
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Main.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class JanitorApiControllerTest {
  private static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("key1", "value1", "key2", "value2");
  private static final OffsetDateTime CREATION = JanitorDao.currentOffsetDateTime();
  private static final OffsetDateTime EXPIRATION =
      JanitorDao.currentOffsetDateTime().plusMinutes(10);
  private static final String PROJECT_PARENT = "folders/1234";
  private static final String CLAIM_EMAIL_KEY = "OIDC_CLAIM_email";
  private static final String CLAIM_SUBJECT_KEY = "OIDC_CLAIM_user_id";
  private static final String CLAIM_TOKEN_KEY = "OIDC_ACCESS_token";
  private static final String CLAIM_AUTH_KEY = "Authorization";
  private static final String ADMIN_USER_EMAIL = "test1@email.com";
  private static final String ADMIN_SUBJECT_ID = "test1";
  private static final String ADMIN_TOKEN = "1234.ab-CD";
  private static final String ADMIN_BEARER_TOKEN = "Bearer ".concat(ADMIN_TOKEN);

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
            .resourceMetadata(new ResourceMetadata().googleProjectParent(PROJECT_PARENT))
            .creation(CREATION)
            .expiration(EXPIRATION)
            .labels(DEFAULT_LABELS);

    String createResponse =
        this.mvc
            .perform(
                post("/api/janitor/v1/resource")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CLAIM_EMAIL_KEY, ADMIN_USER_EMAIL)
                    .header(CLAIM_SUBJECT_KEY, ADMIN_SUBJECT_ID)
                    .header(CLAIM_TOKEN_KEY, ADMIN_TOKEN)
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
                    .header(CLAIM_EMAIL_KEY, ADMIN_USER_EMAIL)
                    .header(CLAIM_SUBJECT_KEY, ADMIN_SUBJECT_ID)
                    .header(CLAIM_TOKEN_KEY, ADMIN_TOKEN))
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
    assertEquals(PROJECT_PARENT, trackedResourceInfo.getMetadata().getGoogleProjectParent());
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
                .header(CLAIM_EMAIL_KEY, ADMIN_USER_EMAIL)
                .header(CLAIM_SUBJECT_KEY, ADMIN_SUBJECT_ID)
                .header(CLAIM_TOKEN_KEY, ADMIN_TOKEN))
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
  public void createResource_notAuthorizedBadToken() throws Exception {
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
                .header(CLAIM_EMAIL_KEY, ADMIN_USER_EMAIL)
                .header(CLAIM_SUBJECT_KEY, ADMIN_SUBJECT_ID)

                // This should cause failure, as ADMIN_TOKEN is not a properly formatted token for
                // the "Authorization' header.
                .header(CLAIM_AUTH_KEY, ADMIN_TOKEN)
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
                .header(CLAIM_EMAIL_KEY, ADMIN_USER_EMAIL)
                .header(CLAIM_SUBJECT_KEY, ADMIN_SUBJECT_ID)
                .header(CLAIM_TOKEN_KEY, ADMIN_TOKEN))
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
            .expiration(EXPIRATION.plusMinutes(1))
            .labels(DEFAULT_LABELS);

    String createResponse1 =
        this.mvc
            .perform(
                post("/api/janitor/v1/resource")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body1))
                    .header(CLAIM_EMAIL_KEY, ADMIN_USER_EMAIL)
                    .header(CLAIM_SUBJECT_KEY, ADMIN_SUBJECT_ID)
                    .header(CLAIM_TOKEN_KEY, ADMIN_TOKEN))
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
                    .header(CLAIM_EMAIL_KEY, ADMIN_USER_EMAIL)
                    .header(CLAIM_SUBJECT_KEY, ADMIN_SUBJECT_ID)
                    .header(CLAIM_AUTH_KEY, ADMIN_BEARER_TOKEN))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id1 = objectMapper.readValue(createResponse1, CreatedResource.class).getId();
    String id2 = objectMapper.readValue(createResponse2, CreatedResource.class).getId();

    String getResponseResourceUid =
        this.mvc
            .perform(
                get("/api/janitor/v1/resource")
                    .queryParam("cloudResourceUid", objectMapper.writeValueAsString(resourceUid))
                    .header(CLAIM_EMAIL_KEY, ADMIN_USER_EMAIL)
                    .header(CLAIM_SUBJECT_KEY, ADMIN_SUBJECT_ID)
                    .header(CLAIM_TOKEN_KEY, ADMIN_TOKEN))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    TrackedResourceInfoList resourceInfoList =
        objectMapper.readValue(getResponseResourceUid, TrackedResourceInfoList.class);
    assertThat(
        resourceInfoList.getResources().stream()
            .map(TrackedResourceInfo::getId)
            .collect(Collectors.toList()),
        Matchers.containsInAnyOrder(id1, id2));

    String getResponseResourceState =
        this.mvc
            .perform(
                get("/api/janitor/v1/resource")
                    .queryParam("state", ResourceState.READY.toString())
                    .header(CLAIM_EMAIL_KEY, ADMIN_USER_EMAIL)
                    .header(CLAIM_SUBJECT_KEY, ADMIN_SUBJECT_ID)
                    .header(CLAIM_TOKEN_KEY, ADMIN_TOKEN))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    resourceInfoList =
        objectMapper.readValue(getResponseResourceState, TrackedResourceInfoList.class);
    assertThat(
        resourceInfoList.getResources().stream()
            .map(TrackedResourceInfo::getId)
            .collect(Collectors.toList()),
        Matchers.containsInAnyOrder(id2));

    String getResponseLimit =
        this.mvc
            .perform(
                get("/api/janitor/v1/resource")
                    .queryParam("limit", String.valueOf(1))
                    .header(CLAIM_EMAIL_KEY, ADMIN_USER_EMAIL)
                    .header(CLAIM_SUBJECT_KEY, ADMIN_SUBJECT_ID)
                    .header(CLAIM_TOKEN_KEY, ADMIN_TOKEN))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    resourceInfoList = objectMapper.readValue(getResponseLimit, TrackedResourceInfoList.class);
    assertThat(resourceInfoList.getResources(), Matchers.hasSize(1));

    this.mvc
        .perform(
            get("/api/janitor/v1/resource")
                // Offset but no limit.
                .queryParam("limit", String.valueOf(0))
                .queryParam("offset", String.valueOf(1))
                .header(CLAIM_EMAIL_KEY, ADMIN_USER_EMAIL)
                .header(CLAIM_SUBJECT_KEY, ADMIN_SUBJECT_ID)
                .header(CLAIM_TOKEN_KEY, ADMIN_TOKEN))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(status().is(400));
  }

  @Test
  public void bumpErrors() throws Exception {
    this.mvc
        .perform(
            put("/api/janitor/v1/resource/bumpErrors")
                .header(CLAIM_EMAIL_KEY, ADMIN_USER_EMAIL)
                .header(CLAIM_SUBJECT_KEY, ADMIN_SUBJECT_ID)
                .header(CLAIM_TOKEN_KEY, ADMIN_TOKEN))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(status().is(204));
  }

  @Test
  public void bumpErrors_notAuthorized() throws Exception {
    this.mvc
        .perform(put("/api/janitor/v1/resource/bumpErrors"))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(status().isUnauthorized());
  }
}
