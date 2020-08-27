package bio.terra.janitor.service.iam;

import javax.servlet.http.HttpServletRequest;

/** An interface for extracting {@link AuthenticatedUserRequest} from {@link HttpServletRequest}. */
public interface AuthenticatedUserRequestFactory {
  AuthenticatedUserRequest from(HttpServletRequest servletRequest);
}
