package bio.terra.janitor.common;

import com.google.auth.oauth2.ServiceAccountCredentials;

/** Utilities for handling credentials. */
public class CredentialUtils {
  /** Gets the {@link ServiceAccountCredentials} from a file path. */
  public static ServiceAccountCredentials getGoogleCredentialsOrDie(String serviceAccountPath) {
    try {
      return ServiceAccountCredentials.fromStream(
          Thread.currentThread().getContextClassLoader().getResourceAsStream(serviceAccountPath));
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to load GoogleCredentials from configuration" + serviceAccountPath, e);
    }
  }
}
