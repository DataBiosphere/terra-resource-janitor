package bio.terra.janitor.service.iam;

import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Implementation of {link AuthenticatedUserRequestFactory} when HTTP requests enter from Apache
 * Proxy. In this scenario, Janitor service is deployed behind Apache Proxy, and Apache Proxy
 */
@Component
public class ProxiedAuthenticatedUserRequestFactory implements AuthenticatedUserRequestFactory {

  /** Method to build an AuthenticatedUserRequest from data available to the controller. */
  public AuthenticatedUserRequest from(HttpServletRequest servletRequest) {
    String token =
        Optional.ofNullable(servletRequest.getHeader(AuthHeaderKeys.OIDC_ACCESS_TOKEN.getKeyName()))
            .orElseGet(
                () -> {
                  String authHeader =
                      servletRequest.getHeader(AuthHeaderKeys.AUTHORIZATION.getKeyName());
                  return StringUtils.substring(authHeader, "Bearer:".length());
                });
    return new AuthenticatedUserRequest()
        .email(servletRequest.getHeader(AuthHeaderKeys.OIDC_CLAIM_EMAIL.getKeyName()))
        .subjectId(servletRequest.getHeader(AuthHeaderKeys.OIDC_CLAIM_USER_ID.getKeyName()))
        .token(Optional.ofNullable(token));
  }
}
