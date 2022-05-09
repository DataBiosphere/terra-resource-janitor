package bio.terra.janitor.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.janitor.common.BaseUnitTest;
import org.junit.jupiter.api.Test;

/**
 * Unit tests to verify that changes to Java classes do not break backwards compatibility with
 * values that might already exist in databases.
 *
 * <p>If your change causes one of these tests to fail, consider whether it is backwards compatible
 * instead of modifying the test to pass.
 */
public class BackwardsCompatibilityTest extends BaseUnitTest {

  /**
   * Change detection test for existing {@link ResourceType} enum values. More values should be
   * added as the enum expands.
   */
  @Test
  public void resourceType() {
    assertEquals(ResourceType.GOOGLE_BIGQUERY_TABLE, ResourceType.valueOf("GOOGLE_BIGQUERY_TABLE"));
    assertEquals(
        ResourceType.GOOGLE_BIGQUERY_DATASET, ResourceType.valueOf("GOOGLE_BIGQUERY_DATASET"));
    assertEquals(ResourceType.GOOGLE_BLOB, ResourceType.valueOf("GOOGLE_BLOB"));
    assertEquals(ResourceType.GOOGLE_BUCKET, ResourceType.valueOf("GOOGLE_BUCKET"));
    assertEquals(
        ResourceType.GOOGLE_NOTEBOOK_INSTANCE, ResourceType.valueOf("GOOGLE_NOTEBOOK_INSTANCE"));
    assertEquals(ResourceType.GOOGLE_PROJECT, ResourceType.valueOf("GOOGLE_PROJECT"));
    assertEquals(ResourceType.AZURE_PUBLIC_IP, ResourceType.valueOf("AZURE_PUBLIC_IP"));
    assertEquals(
        ResourceType.AZURE_NETWORK_SECURITY_GROUP,
        ResourceType.valueOf("AZURE_NETWORK_SECURITY_GROUP"));
    assertEquals(ResourceType.AZURE_NETWORK, ResourceType.valueOf("AZURE_NETWORK"));
    assertEquals(ResourceType.AZURE_DISK, ResourceType.valueOf("AZURE_DISK"));
    assertEquals(ResourceType.AZURE_VIRTUAL_MACHINE, ResourceType.valueOf("AZURE_VIRTUAL_MACHINE"));
    assertEquals(ResourceType.AZURE_RELAY, ResourceType.valueOf("AZURE_RELAY"));
    assertEquals(
        ResourceType.AZURE_RELAY_CONNECTION, ResourceType.valueOf("AZURE_RELAY_CONNECTION"));
    assertEquals(
        ResourceType.AZURE_CONTAINER_INSTANCE, ResourceType.valueOf("AZURE_CONTAINER_INSTANCE"));
    assertEquals(ResourceType.TERRA_WORKSPACE, ResourceType.valueOf("TERRA_WORKSPACE"));
  }

  /**
   * Change detection test for existing {@link TrackedResourceState }enum values. More values should
   * be added as the enum expands.
   */
  @Test
  public void trackedResourceState() {
    assertEquals(TrackedResourceState.READY, TrackedResourceState.valueOf("READY"));
    assertEquals(TrackedResourceState.CLEANING, TrackedResourceState.valueOf("CLEANING"));
    assertEquals(TrackedResourceState.DONE, TrackedResourceState.valueOf("DONE"));
    assertEquals(TrackedResourceState.ERROR, TrackedResourceState.valueOf("ERROR"));
    assertEquals(TrackedResourceState.ABANDONED, TrackedResourceState.valueOf("ABANDONED"));
    assertEquals(TrackedResourceState.DUPLICATED, TrackedResourceState.valueOf("DUPLICATED"));
  }

  /**
   * Change detection test for existing {@link CleanupFlightState }enum values. More values should
   * be added as the enum expands.
   */
  @Test
  public void cleanupFlightState() {
    assertEquals(CleanupFlightState.INITIATING, CleanupFlightState.valueOf("INITIATING"));
    assertEquals(CleanupFlightState.IN_FLIGHT, CleanupFlightState.valueOf("IN_FLIGHT"));
    assertEquals(CleanupFlightState.FINISHING, CleanupFlightState.valueOf("FINISHING"));
    assertEquals(CleanupFlightState.FINISHED, CleanupFlightState.valueOf("FINISHED"));
    assertEquals(CleanupFlightState.FATAL, CleanupFlightState.valueOf("FATAL"));
    assertEquals(CleanupFlightState.LOST, CleanupFlightState.valueOf("LOST"));
  }
}
