package bio.terra.janitor.db;

/**
 * Enums to represent cloud resource type we supported in DB schema.
 *
 * <p>These enums are recorded as strings in the database and should therefore not be removed or
 * modified.
 *
 * <p>It is ok to add new values. Also update the BackwardsCompatibilityTest.
 */
public enum ResourceType {
  GOOGLE_BIGQUERY_TABLE,
  GOOGLE_BIGQUERY_DATASET,
  GOOGLE_BLOB,
  GOOGLE_BUCKET,
  GOOGLE_NOTEBOOK_INSTANCE,
  GOOGLE_PROJECT,
  AZURE_PUBLIC_IP,
  AZURE_NETWORK,
  AZURE_NETWORK_SECURITY_GROUP,
  AZURE_DISK,
  AZURE_VIRTUAL_MACHINE,
  AZURE_RELAY,
  AZURE_RELAY_CONNECTION,
  AZURE_CONTAINER_INSTANCE,
  TERRA_WORKSPACE,
  AZURE_MANAGED_IDENTITY,
  AZURE_STORAGE_CONTAINER,
  AZURE_DATABASE,
  AZURE_KUBERNETES_NAMESPACE,
  AZURE_BATCH_POOL,
}
