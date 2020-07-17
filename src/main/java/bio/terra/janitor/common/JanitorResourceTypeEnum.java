package bio.terra.janitor.common;

/**
 * Enums mapped from {@link null} to our DB schema. The original value can be found at <a
 * href="https://github.com/DataBiosphere/terra-cloud-resource-lib/blob/master/cloud-resource-schema/src/main/resources/cloud_resources_uid.yaml">terra-cloud-resource-lib
 * repo</a>
 */
public enum JanitorResourceTypeEnum {
  GOOGLE_BIG_QUERY_TABLE,
  GOOGLE_BIG_QUERY_DATASET,
  GOOGLE_BLOB,
  GOOGLE_BUCKET,
  GOOGLE_PROJECT;
}
