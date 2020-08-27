package bio.terra.janitor.service.iam;

import java.util.UUID;

/** Captures the inbound authentication information. */
public class AuthenticatedUserRequest {
  private String email;
  private String subjectId;
  private UUID requestId;

  public AuthenticatedUserRequest() {
    this.requestId = UUID.randomUUID();
  }

  public AuthenticatedUserRequest(String email, String subjectId) {
    this.email = email;
    this.subjectId = subjectId;
  }

  public String getSubjectId() {
    return subjectId;
  }

  public AuthenticatedUserRequest subjectId(String subjectId) {
    this.subjectId = subjectId;
    return this;
  }

  public String getEmail() {
    return email;
  }

  public AuthenticatedUserRequest email(String email) {
    this.email = email;
    return this;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public AuthenticatedUserRequest reqId(UUID reqId) {
    this.requestId = reqId;
    return this;
  }
}
