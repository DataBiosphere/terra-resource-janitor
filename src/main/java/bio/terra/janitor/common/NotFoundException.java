package bio.terra.janitor.common;

import bio.terra.janitor.common.exception.ErrorReportException;
import org.springframework.http.HttpStatus;

/** Exception when resource met criteria is not found. */
public class NotFoundException extends ErrorReportException {
  private static final HttpStatus thisStatus = HttpStatus.NOT_FOUND;

  public NotFoundException(String message) {
    super(message, null, thisStatus);
  }
}
