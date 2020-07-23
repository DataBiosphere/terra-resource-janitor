package bio.terra.janitor.service.cleanup.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import com.google.common.base.Preconditions;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * A step to block until the latch is released to be used for testing.
 *
 * <p>This step relies on in-memory state and does not work across services or after a service
 * restart. It is only useful for testing.
 */
public class LatchStep implements Step {
  private static ConcurrentHashMap<String, CountDownLatch> latches = new ConcurrentHashMap<>();

  private static final String FLIGHT_MAP_KEY = "LatchStep";

  /**
   * Initialize a new latch at {@code key} and add a parameter for it to the {@link FlightMap}.
   * Replaces any existing latches with the same key.
   */
  public static void createLatch(FlightMap flightMap, String latchKey) {
    latches.put(latchKey, new CountDownLatch(1));
    flightMap.put(FLIGHT_MAP_KEY, latchKey);
  }

  /** Releases a latch added with {@link #createLatch(FlightMap, String)}. */
  public static void releaseLatch(String latchKey) {
    latches.get(latchKey).countDown();
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    String latchKey = flightContext.getInputParameters().get(FLIGHT_MAP_KEY, String.class);
    CountDownLatch latch = latches.get(latchKey);
    Preconditions.checkNotNull(latch, "Expected a latch to exist with key %s", latchKey);
    latch.await();
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
