package bio.terra.janitor.db;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.common.ResourceType;
import bio.terra.janitor.common.ResourceTypeVisitor;
import bio.terra.janitor.common.exception.InvalidResourceUidException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JanitorDao {
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public JanitorDao(JanitorJdbcConfiguration jdbcConfiguration) {
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  /**
   * Creates the tracked_resource record and adding labels.
   *
   * <p>Note that we assume int input {@code cloudResourceUid} is valid.
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public TrackedResourceId createResource(
      CloudResourceUid cloudResourceUid,
      Map<String, String> labels,
      Instant creation,
      Instant expiration) {
    // TODO(yonghao): Solution for handling duplicate CloudResourceUid.
    String sql =
        "INSERT INTO tracked_resource (id, resource_uid, resource_type, creation, expiration, state) values "
            + "(:id, :resource_uid::jsonb, :resource_type, :creation, :expiration, :state)";

    TrackedResourceId trackedResourceId =
        TrackedResourceId.builder().setId(UUID.randomUUID()).build();

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", trackedResourceId.id())
            .addValue("resource_uid", serialize(cloudResourceUid))
            .addValue(
                "resource_type", new ResourceTypeVisitor().accept(cloudResourceUid).toString())
            .addValue("creation", creation.atOffset(ZoneOffset.UTC))
            .addValue("state", "READY")
            .addValue("expiration", expiration.atOffset(ZoneOffset.UTC));

    jdbcTemplate.update(sql, params);

    if (labels != null) {
      String insertLabelSql =
          "INSERT INTO label (tracked_resource_id, key, value) values "
              + "(:tracked_resource_id, :key, :value)";

      MapSqlParameterSource[] sqlParameterSourceList =
          labels.entrySet().stream()
              .map(
                  entry ->
                      new MapSqlParameterSource()
                          .addValue("tracked_resource_id", trackedResourceId.id())
                          .addValue("key", entry.getKey())
                          .addValue("value", entry.getValue()))
              .toArray(size -> new MapSqlParameterSource[labels.size()]);

      jdbcTemplate.batchUpdate(insertLabelSql, sqlParameterSourceList);
    }
    return trackedResourceId;
  }

  /**
   * Returns up to {@code limit} {@link TrackedResource}s that are ready to be scheduled for
   * cleaning.
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public List<TrackedResource> retrieveSchedulableResources(Instant now, int limit) {
    String sql =
        "SELECT id, resource_type, resource_uid, creation, expiration, state FROM tracked_resource "
            + "WHERE state = :ready and expiration < :now LIMIT :limit";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("ready", TrackedResourceState.READY.toString())
            .addValue("now", now.atOffset(ZoneOffset.UTC))
            .addValue("limit", limit);
    return jdbcTemplate.query(sql, params, TRACKED_RESOURCE_ROW_MAPPER);
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public Optional<TrackedResource> updateResourceForCleaning(Instant now, String flightId) {
    UUID id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM tracked_resource "
                + "WHERE state = :ready and expiration < :now LIMIT 1",
            new MapSqlParameterSource()
                .addValue("ready", TrackedResourceState.READY.toString())
                .addValue("now", now.atOffset(ZoneOffset.UTC)),
            UUID.class);
    if (id == null) {
      // No resources ready to schedule.
      return Optional.empty();
    }

    TrackedResourceId resourceToClean = TrackedResourceId.builder().setId(id).build();
    jdbcTemplate.update(
        "INSERT INTO cleanup_flight (tracked_resource_id, flight_id, state) "
            + "values (:tracked_resource_id, :flight_id, :initiating)",
        new MapSqlParameterSource()
            .addValue("tracked_resource_id", resourceToClean.id())
            .addValue("flight_id", flightId)
            .addValue("initiating", CleanupFlightState.INITIATING));
    return Optional.of(
        jdbcTemplate.queryForObject(
            "UPDATE tracked_resource SET state = :cleaning WHERE id = :id "
                + "RETURNING id, resource_type, resource_uid, creation, expiration, state",
            new MapSqlParameterSource()
                .addValue("cleaning", TrackedResourceState.CLEANING.toString())
                .addValue("id", resourceToClean.id()),
            TRACKED_RESOURCE_ROW_MAPPER));
  }

  /** Returns up to {@code limit} resources with a cleanup flight in the given state. */
  public List<TrackedResourceAndFlight> retrieveResourcesWith(
      CleanupFlightState flightState, int limit) {
    String sql =
        "SELECT tr.id, tr.resource_type, tr.resource_uid, tr.creation, tr.expiration, tr.state, "
            + "cf.flight_id, cf.flight_state FROM tracked_resource tr"
            + "JOIN cleanup_flight cf ON tr.id = cf.tracked_resource_id "
            + "WHERE cf.state = :flight_state LIMIT :limit";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("flight_state", flightState.toString())
            .addValue("limit", limit);
    return jdbcTemplate.query(
        sql,
        params,
        (rs, rowNum) ->
            TrackedResourceAndFlight.create(
                TRACKED_RESOURCE_ROW_MAPPER.mapRow(rs, rowNum),
                CLEANUP_FLIGHT_ROW_MAPPER.mapRow(rs, rowNum)));
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void setFlightState(
      TrackedResourceId trackedResourceId, String flightId, CleanupFlightState flightState) {
    String sql = "UPDATE cleanup_flight SET state = :state WHERE flight_id = :flight_id;";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("cleaning", TrackedResourceState.CLEANING.toString())
            .addValue("ready", TrackedResourceState.READY.toString())
            .addValue("id", trackedResourceId);
    jdbcTemplate.update(sql, params);
  }

  private static final RowMapper<TrackedResource> TRACKED_RESOURCE_ROW_MAPPER =
      (rs, rowNum) ->
          TrackedResource.builder()
              .id(TrackedResourceId.builder().setId(rs.getObject("id", UUID.class)).build())
              .resourceType(ResourceType.valueOf(rs.getString("resource_type")))
              .cloudResourceUid(deserialize(rs.getString("resource_uid")))
              .creationTime(rs.getObject("creation", OffsetDateTime.class).toInstant())
              .expirationTime(rs.getObject("expiration", OffsetDateTime.class).toInstant())
              .trackedResourceState(TrackedResourceState.valueOf(rs.getString("state")))
              .build();

  private static final RowMapper<CleanupFlight> CLEANUP_FLIGHT_ROW_MAPPER =
      (rs, rowNum) ->
          CleanupFlight.create(
              rs.getString("flight_id"), CleanupFlightState.valueOf(rs.getString("flight_state")));

  @VisibleForTesting
  static String serialize(CloudResourceUid resource) {
    try {
      return new ObjectMapper().writeValueAsString(resource);
    } catch (JsonProcessingException e) {
      throw new InvalidResourceUidException("Failed to serialize CloudResourceUid");
    }
  }

  @VisibleForTesting
  static CloudResourceUid deserialize(String resource) {
    try {
      return new ObjectMapper().readValue(resource, CloudResourceUid.class);
    } catch (JsonProcessingException e) {
      throw new InvalidResourceUidException("Failed to deserialize CloudResourceUid: " + resource);
    }
  }

  /** A {@link TrackedResource} with the {@link CleanupFlight} associated with it. */
  @AutoValue
  public abstract static class TrackedResourceAndFlight {
    public abstract TrackedResource trackedResource();

    public abstract CleanupFlight cleanupFlight();

    public static TrackedResourceAndFlight create(
        TrackedResource trackedResource, CleanupFlight cleanupFlight) {
      return new AutoValue_JanitorDao_TrackedResourceAndFlight(trackedResource, cleanupFlight);
    }
  }
}
