package bio.terra.janitor.common.exception;

/**
 * Exception when {@link bio.terra.generated.model.CreateResourceRequestBody} message is invalid.
 */
public class InvalidMessageException extends RuntimeException {

  public InvalidMessageException(String message) {
    super(message);
  }

  public InvalidMessageException(String message, Throwable cause) {
    super(message, cause);
  }
}
