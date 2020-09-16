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

  /**
   * Wait for a duration longer than reporting duration (5s) to ensure metrics are exported.
   *
   * <p>Values from
   * https://github.com/census-instrumentation/opencensus-java/blob/5be70440b53815eec1ab59513390aadbcec5cc9c/examples/src/main/java/io/opencensus/examples/helloworld/QuickStart.java#L106
   */
  public static void sleepForMetricsExport() throws InterruptedException {
    Thread.sleep(5100);
  }
}
