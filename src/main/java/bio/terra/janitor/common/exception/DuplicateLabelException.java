package bio.terra.janitor.common.exception;

public class DuplicateLabelException extends BadRequestException {

  public DuplicateLabelException(String message) {
    super(message);
  }

  public DuplicateLabelException(String message, Throwable cause) {
    super(message, cause);
  }
}
