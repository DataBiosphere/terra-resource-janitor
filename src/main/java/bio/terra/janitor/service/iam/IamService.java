package bio.terra.janitor.service.iam;

import bio.terra.janitor.app.configuration.IamConfiguration;
import bio.terra.janitor.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * IAM Service which handles authorization. Currently, Janitor uses a simple user email allow list
 * to check user permission.
 *
 * <p>TODO(PF-81): Switch to use SAM when GKE SAM is ready to use.
 */
@Component
public class IamService {
  private final IamConfiguration iamConfiguration;

  @Autowired
  public IamService(IamConfiguration iamConfiguration) {
    this.iamConfiguration = iamConfiguration;
  }

  /** Check if user is an administrator. Throws {@link UnauthorizedException} if not. */
  public void isAdminUser(AuthenticatedUserRequest userReq) {
    if (iamConfiguration.isConfigBasedAuthZEnabled()) {
      return;
    }
    boolean isAdmin = iamConfiguration.getAdminUsers().contains(userReq.getEmail());
    if (!isAdmin)
      throw new UnauthorizedException(
          "User " + userReq.getEmail() + " is not Janitor's administrator.");
  }
}
