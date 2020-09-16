package bio.terra.janitor.service.cleanup;

/** Testing utilities for cleanup. */
class CleanupTestUtils {
  private CleanupTestUtils() {}
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
