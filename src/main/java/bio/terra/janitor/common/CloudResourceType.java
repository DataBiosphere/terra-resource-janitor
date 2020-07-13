package bio.terra.janitor.common;

import com.google.common.collect.ImmutableMap;

/**
 * Enums mapped from {@link OneOfCreateResourceRequestBodyResourceUid} to our DB schema. The
 * original value can be found at <a
 * href="https://github.com/DataBiosphere/terra-cloud-resource-lib/blob/master/cloud-resource-schema/src/main/resources/cloud_resources_uid.yaml">terra-cloud-resource-lib
 * repo</a>
 */
public class CloudResourceType {

  public enum Enum {
    UNKNOWN,
    GOOGLE_BIG_QUERY_TABLE,
    GOOGLE_BIG_QUERY_DATASET,
    GOOGLE_BLOB,
    GOOGLE_BUCKET,
    GOOGLE_PROJECT;
  }

  private static final ImmutableMap<Class<?>, Enum> CLOUD_RESOURCE_MAP =
      new ImmutableMap.Builder<Class<?>, Enum>()
          .put(GoogleBigQueryTableUid.class, Enum.GOOGLE_BIG_QUERY_TABLE)
          .put(GoogleBigQueryDatasetUid.class, Enum.GOOGLE_BIG_QUERY_DATASET)
          .put(GoogleBlobUid.class, Enum.GOOGLE_BLOB)
          .put(GoogleBucketUid.class, Enum.GOOGLE_BUCKET)
          .put(GoogleProjectUid.class, Enum.GOOGLE_PROJECT)
          .build();

  public static Enum getCloudResourceType(
      OneOfCreateResourceRequestBodyResourceUid cloudResourceUid) {
    return CLOUD_RESOURCE_MAP.getOrDefault(cloudResourceUid.getClass(), Enum.UNKNOWN);
  }
}
