package bio.terra.janitor.common.exception;

/** Exception when {@link bio.terra.generated.model.CloudResourceUid} in request is invalid. */
public class InvalidResourceUidException extends BadRequestException {

  public InvalidResourceUidException(String message) {
    super(message);
  }

  public InvalidResourceUidException(String message, Throwable cause) {
    super(message, cause);
  }
}
