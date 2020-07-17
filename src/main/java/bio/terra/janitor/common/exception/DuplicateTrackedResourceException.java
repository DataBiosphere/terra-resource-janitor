package bio.terra.janitor.common.exception;

/** Exception when inserting into tracked_resource table while there is already a row with the same key. */
public class DuplicateTrackedResourceException extends BadRequestException {

  public DuplicateTrackedResourceException(String message) {
    super(message);
  }

  public DuplicateTrackedResourceException(String message, Throwable cause) {
    super(message, cause);
  }
}
