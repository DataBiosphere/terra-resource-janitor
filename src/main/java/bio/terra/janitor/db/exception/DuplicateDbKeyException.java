package bio.terra.janitor.db.exception;

import bio.terra.janitor.common.exception.BadRequestException;

/** Exception when inserting postgres table while there is already a row with the same key. */
public class DuplicateDbKeyException extends BadRequestException {

  public DuplicateDbKeyException(String message) {
    super(message);
  }

  public DuplicateDbKeyException(String message, Throwable cause) {
    super(message, cause);
  }
}
