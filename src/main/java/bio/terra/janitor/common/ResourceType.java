package bio.terra.janitor.common;

/** Enums to represent cloud resource type we supported in DB schema.*/
public enum ResourceType {
  GOOGLE_BIG_QUERY_TABLE,
  GOOGLE_BIG_QUERY_DATASET,
  GOOGLE_BLOB,
  GOOGLE_BUCKET,
  GOOGLE_PROJECT;
}
