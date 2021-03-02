package bio.terra.janitor.common.exception;

import bio.terra.common.exception.BadRequestException;

/**
 * Exception when {@link bio.terra.janitor.generated.model.CloudResourceUid} in request is invalid.
 */
public class InvalidResourceUidException extends BadRequestException {

  public InvalidResourceUidException(String message) {
    super(message);
  }

  public InvalidResourceUidException(String message, Throwable cause) {
    super(message, cause);
  }
}
