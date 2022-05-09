package bio.terra.janitor.common.exception;

import javax.ws.rs.BadRequestException;

/**
 * Janitor impersonates test users to clean up WSM workspaces (and possibly for other purposes in
 * the future). The Janitor SA can only impersonate users in a specified test domain. This error
 * occurs when a cleanup request specifies a user outside that test user domain.
 */
public class InvalidTestUserException extends BadRequestException {
  public InvalidTestUserException(String message) {
    super(message);
  }
}
