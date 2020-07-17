package bio.terra.janitor.common.exception;

public class InvalidResourceUidException extends BadRequestException {

  public InvalidResourceUidException(String message) {
    super(message);
  }

  public InvalidResourceUidException(String message, Throwable cause) {
    super(message, cause);
  }
}
