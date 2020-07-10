package bio.terra.janitor.common;

import bio.terra.generated.model.CloudResourceUid;
import com.google.common.collect.ImmutableMap;

/**
 * Enums mapped from {@link CloudResourceUid} to our DB schema. The original value can be found at
 * <a
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

  private static final ImmutableMap<String, Enum> CLOUD_RESOURCE_MAP =
      new ImmutableMap.Builder<String, Enum>()
          .put("googleBigQueryTableUid", Enum.GOOGLE_BIG_QUERY_TABLE)
          .put("googleBigQueryDatasetUid", Enum.GOOGLE_BIG_QUERY_DATASET)
          .put("googleBucketUid", Enum.GOOGLE_BLOB)
          .put("googleBlobUid", Enum.GOOGLE_BUCKET)
          .put("googleProjectUid", Enum.GOOGLE_PROJECT)
          .build();

  public static Enum getCloudResourceType(CloudResourceUid cloudResourceUid) {
    return CLOUD_RESOURCE_MAP.getOrDefault(cloudResourceUid.getResourceType(), Enum.UNKNOWN);
  }
}
