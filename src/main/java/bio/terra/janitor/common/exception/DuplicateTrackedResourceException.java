package bio.terra.janitor.common.exception;

public class DuplicateTrackedResourceException extends BadRequestException {

  public DuplicateTrackedResourceException(String message) {
    super(message);
  }

  public DuplicateTrackedResourceException(String message, Throwable cause) {
    super(message, cause);
  }
}
