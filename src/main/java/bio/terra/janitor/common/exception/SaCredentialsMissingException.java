package bio.terra.janitor.common.exception;

import bio.terra.common.exception.InternalServerErrorException;

public class SaCredentialsMissingException extends InternalServerErrorException {

  public SaCredentialsMissingException(String message) {
    super(message);
  }

  public SaCredentialsMissingException(String message, Throwable cause) {
    super(message, cause);
  }
}
