package bio.terra.janitor.service.janitor;

import bio.terra.janitor.db.TrackRequest;
import bio.terra.janitor.db.TrackedResource;
import bio.terra.janitor.db.TrackedResourceAndLabels;
import bio.terra.janitor.db.TrackedResourceState;
import bio.terra.janitor.generated.model.CreateResourceRequestBody;
import bio.terra.janitor.generated.model.ResourceState;
import bio.terra.janitor.generated.model.TrackedResourceInfo;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.EnumBiMap;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Helper class for converting to and from the request/response model format. */
public class ModelUtils {
  private ModelUtils() {}

  private static final BiMap<TrackedResourceState, ResourceState> stateMap =
      EnumBiMap.create(TrackedResourceState.class, ResourceState.class);

  static {
    stateMap.put(TrackedResourceState.READY, ResourceState.READY);
    stateMap.put(TrackedResourceState.CLEANING, ResourceState.CLEANING);
    stateMap.put(TrackedResourceState.ERROR, ResourceState.ERROR);
    stateMap.put(TrackedResourceState.DONE, ResourceState.DONE);
    stateMap.put(TrackedResourceState.ABANDONED, ResourceState.ABANDONED);
    stateMap.put(TrackedResourceState.DUPLICATED, ResourceState.DUPLIATED);
  }

  public static TrackRequest createTrackRequest(CreateResourceRequestBody body) {
    return TrackRequest.builder()
        .cloudResourceUid(body.getResourceUid())
        .creation(body.getCreation().toInstant())
        .expiration(body.getExpiration().toInstant())
        .labels(body.getLabels() == null ? ImmutableMap.of() : body.getLabels())
        .build();
  }

  public static TrackedResourceInfo createInfo(TrackedResourceAndLabels resourceAndLabels) {
    TrackedResource resource = resourceAndLabels.trackedResource();
    return new TrackedResourceInfo()
        .id(resource.trackedResourceId().toString())
        .resourceUid(resource.cloudResourceUid())
        .state(convert(resource.trackedResourceState()))
        .creation(OffsetDateTime.ofInstant(resource.creation(), ZoneOffset.UTC))
        .expiration(OffsetDateTime.ofInstant(resource.expiration(), ZoneOffset.UTC))
        .labels(resourceAndLabels.labels());
  }

  public static ResourceState convert(TrackedResourceState state) {
    ResourceState converted = stateMap.get(state);
    Preconditions.checkNotNull(
        converted, String.format("Unable to convert TrackedResourceState %s", state));
    return converted;
  }

  public static TrackedResourceState convert(ResourceState state) {
    TrackedResourceState converted = stateMap.inverse().get(state);
    Preconditions.checkNotNull(
        converted, String.format("Unable to convert ResourceState %s", state));
    return converted;
  }
}
