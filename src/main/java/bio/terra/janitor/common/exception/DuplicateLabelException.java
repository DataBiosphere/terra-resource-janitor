package bio.terra.janitor.common.exception;

/** Exception when inserting into Label table while there is already a label with the same key. */
public class DuplicateLabelException extends BadRequestException {

  public DuplicateLabelException(String message) {
    super(message);
  }

  public DuplicateLabelException(String message, Throwable cause) {
    super(message, cause);
  }
}
