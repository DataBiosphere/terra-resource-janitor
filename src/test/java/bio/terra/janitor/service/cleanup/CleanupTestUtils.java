package bio.terra.janitor.service.cleanup;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Testing utilities for cleanup. */
class CleanupTestUtils {
  private CleanupTestUtils() {}

  public static void pollUntil(Supplier<Boolean> condition, Duration period, int maxNumPolls)
      throws InterruptedException {
    int numPolls = 0;
    while (numPolls < maxNumPolls) {
      TimeUnit.MILLISECONDS.sleep(period.toMillis());
      if (condition.get()) {
        return;
      }
      ++numPolls;
    }
    throw new InterruptedException("Polling exceeded maxNumPolls");
  }
}
