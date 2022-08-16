package bio.terra.janitor.service.janitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.janitor.common.BaseUnitTest;
import bio.terra.janitor.db.JanitorDao;
import bio.terra.janitor.db.TrackedResourceId;
import bio.terra.janitor.db.TrackedResourceState;
import bio.terra.janitor.generated.model.CloudResourceUid;
import bio.terra.janitor.generated.model.CreateResourceRequestBody;
import bio.terra.janitor.generated.model.GoogleProjectUid;
import bio.terra.janitor.generated.model.ResourceState;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class JanitorApiServiceTest extends BaseUnitTest {
  private static final OffsetDateTime DEFAULT_TIME = JanitorDao.currentOffsetDateTime();
  private static final String ADMIN_USER_EMAIL = "test1@email.com";
  private static final String ADMIN_SUBJECT_ID = "test1";
  private static final String ADMIN_TOKEN = "1234.ab-CD";

  @Autowired private JanitorApiService janitorApiService;
  @Autowired private JanitorDao janitorDao;

  private static CreateResourceRequestBody createResourceRequest() {
    return new CreateResourceRequestBody()
        .resourceUid(createUniqueId())
        .creation(DEFAULT_TIME)
        .expiration(DEFAULT_TIME);
  }

  private static CloudResourceUid createUniqueId() {
    return new CloudResourceUid()
        .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
  }

  private static AuthenticatedUserRequest createAdminRequest() {
    return AuthenticatedUserRequest.builder()
        .setEmail(ADMIN_USER_EMAIL)
        .setSubjectId(ADMIN_SUBJECT_ID)
        .setToken(ADMIN_TOKEN)
        .build();
  }

  @Test
  public void bumpErrors() {
    String id1 =
        janitorApiService.createResource(createResourceRequest(), createAdminRequest()).getId();
    String id2 =
        janitorApiService.createResource(createResourceRequest(), createAdminRequest()).getId();
    janitorDao.updateResourceState(
        TrackedResourceId.create(UUID.fromString(id1)), TrackedResourceState.ERROR);

    assertEquals(
        ResourceState.ERROR,
        janitorApiService.getResource(id1, createAdminRequest()).get().getState());
    assertEquals(
        ResourceState.READY,
        janitorApiService.getResource(id2, createAdminRequest()).get().getState());

    janitorApiService.bumpErrors(createAdminRequest());
    assertEquals(
        ResourceState.READY,
        janitorApiService.getResource(id1, createAdminRequest()).get().getState());
    assertEquals(
        ResourceState.READY,
        janitorApiService.getResource(id2, createAdminRequest()).get().getState());
  }
}
