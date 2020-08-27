package bio.terra.janitor.service.iam;

import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Implementation of {link AuthenticatedUserRequestFactory} when HTTP requests enter from Apache
 * Proxy. In this scenario, Janitor service is deployed behind Apache Proxy, and Apache proxy clears
 * out any inbound values for these headers, so they are guaranteed to contain valid auth
 * information.
 */
@Component
public class ProxiedAuthenticatedUserRequestFactory implements AuthenticatedUserRequestFactory {

  /** Method to build an AuthenticatedUserRequest from data available to the controller. */
  public AuthenticatedUserRequest from(HttpServletRequest servletRequest) {
    return new AuthenticatedUserRequest()
        .email(servletRequest.getHeader(AuthHeaderKeys.OIDC_CLAIM_EMAIL.getKeyName()))
        .subjectId(servletRequest.getHeader(AuthHeaderKeys.OIDC_CLAIM_USER_ID.getKeyName()));
  }
}
