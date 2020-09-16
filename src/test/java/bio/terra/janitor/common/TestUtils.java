package bio.terra.janitor.common;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Testing utilities shared by unit tests and integration tests. */
public class TestUtils {
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
