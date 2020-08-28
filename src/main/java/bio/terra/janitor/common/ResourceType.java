package bio.terra.janitor.common;

/**
 * Enums to represent cloud resource type we supported in DB schema.
 *
 * <p>These enums are recorded as strings in the database and should therefore not be changed.
 */
public enum ResourceType {
  GOOGLE_BIGQUERY_TABLE,
  GOOGLE_BIGQUERY_DATASET,
  GOOGLE_BLOB,
  GOOGLE_BUCKET,
  GOOGLE_PROJECT;
}
