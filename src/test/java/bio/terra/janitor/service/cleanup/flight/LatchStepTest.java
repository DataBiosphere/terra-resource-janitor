package bio.terra.janitor.service.cleanup.flight;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.janitor.common.BaseUnitTest;
import bio.terra.janitor.service.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@AutoConfigureMockMvc
public class LatchStepTest extends BaseUnitTest {
  @Autowired StairwayComponent stairwayComponent;

  @Test
  public void latchBlocksUntilReleased() throws Exception {
    FlightMap inputs = new FlightMap();
    LatchStep.createLatch(inputs, "bar");

    String flightId = stairwayComponent.get().createFlightId();
    stairwayComponent.get().submit(flightId, LatchFlight.class, inputs);

    TimeUnit.SECONDS.sleep(2);
    assertTrue(stairwayComponent.get().getFlightState(flightId).isActive());

    LatchStep.releaseLatch("bar");
    TimeUnit.SECONDS.sleep(2);
    assertFalse(stairwayComponent.get().getFlightState(flightId).isActive());
  }

  public static class LatchFlight extends Flight {
    public LatchFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new LatchStep());
    }
  }
}
