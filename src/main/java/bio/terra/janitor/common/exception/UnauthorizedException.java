package bio.terra.janitor.common.exception;

import org.springframework.http.HttpStatus;

/** Exception caused by authorization error. */
public class UnauthorizedException extends ErrorReportException {
  private static final HttpStatus thisStatus = HttpStatus.UNAUTHORIZED;

  public UnauthorizedException(String message) {
    super(message, null, thisStatus);
  }
}
