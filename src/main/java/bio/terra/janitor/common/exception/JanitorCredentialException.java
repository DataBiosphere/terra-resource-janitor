package bio.terra.janitor.common.exception;

/** Exception when Janitor failed to handle credentials. */
public class JanitorCredentialException extends RuntimeException {
  public JanitorCredentialException(String message) {
    super(message);
  }

  public JanitorCredentialException(String message, Throwable cause) {
    super(message, cause);
  }
}
