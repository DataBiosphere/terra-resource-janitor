package bio.terra.janitor.db;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.common.ResourceTypeVisitor;
import bio.terra.janitor.common.exception.InvalidResourceUidException;
import com.fasterxml.jackson.annotation.JsonInclude;
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
import org.springframework.dao.support.DataAccessUtils;
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
  public void createResource(TrackedResource resource, Map<String, String> labels) {
    String sql =
        "INSERT INTO tracked_resource (id, resource_uid, resource_type, creation, expiration, state) values "
            + "(:id, :resource_uid::jsonb, :resource_type, :creation, :expiration, :state)";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", resource.trackedResourceId().uuid())
            .addValue("resource_uid", serialize(resource.cloudResourceUid()))
            .addValue(
                "resource_type",
                new ResourceTypeVisitor().accept(resource.cloudResourceUid()).toString())
            .addValue("creation", resource.creation().atOffset(ZoneOffset.UTC))
            .addValue("state", resource.trackedResourceState().toString())
            .addValue("expiration", resource.expiration().atOffset(ZoneOffset.UTC));

    jdbcTemplate.update(sql, params);

    if (labels != null && !labels.isEmpty()) {
      String insertLabelSql =
          "INSERT INTO label (tracked_resource_id, key, value) values "
              + "(:tracked_resource_id, :key, :value)";

      MapSqlParameterSource[] sqlParameterSourceList =
          labels.entrySet().stream()
              .map(
                  entry ->
                      new MapSqlParameterSource()
                          .addValue("tracked_resource_id", resource.trackedResourceId().uuid())
                          .addValue("key", entry.getKey())
                          .addValue("value", entry.getValue()))
              .toArray(MapSqlParameterSource[]::new);

      jdbcTemplate.batchUpdate(insertLabelSql, sqlParameterSourceList);
    }
  }

  /**
   * Retrieves and updates a TrackedResource that is ready and has expired by {@code now} to {@link
   * TrackedResourceState#CLEANING}. Inserts a new {@link CleanupFlight} for that resource as
   * initiating.
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public Optional<TrackedResource> updateResourceForCleaning(Instant now, String flightId) {
    UUID id =
        DataAccessUtils.singleResult(
            jdbcTemplate.queryForList(
                "SELECT id FROM tracked_resource "
                    + "WHERE state = :ready and expiration <= :now LIMIT 1",
                new MapSqlParameterSource()
                    .addValue("ready", TrackedResourceState.READY.toString())
                    .addValue("now", now.atOffset(ZoneOffset.UTC)),
                UUID.class));
    if (id == null) {
      // No resources ready to schedule.
      return Optional.empty();
    }

    TrackedResourceId resourceToClean = TrackedResourceId.create(id);
    jdbcTemplate.update(
        "INSERT INTO cleanup_flight (tracked_resource_id, flight_id, flight_state) "
            + "VALUES (:tracked_resource_id, :flight_id, :initiating)",
        new MapSqlParameterSource()
            .addValue("tracked_resource_id", resourceToClean.uuid())
            .addValue("flight_id", flightId)
            .addValue("initiating", CleanupFlightState.INITIATING.toString()));
    return Optional.of(
        jdbcTemplate.queryForObject(
            "UPDATE tracked_resource SET state = :cleaning WHERE id = :id "
                + "RETURNING id, resource_uid, creation, expiration, state",
            new MapSqlParameterSource()
                .addValue("cleaning", TrackedResourceState.CLEANING.toString())
                .addValue("id", resourceToClean.uuid()),
            TRACKED_RESOURCE_ROW_MAPPER));
  }

  /** Returns up to {@code limit} resources with a cleanup flight in the given state. */
  public List<TrackedResourceAndFlight> retrieveResourcesWith(
      CleanupFlightState flightState, int limit) {
    String sql =
        "SELECT tr.id, tr.resource_uid, tr.creation, tr.expiration, tr.state, "
            + "cf.flight_id, cf.flight_state FROM tracked_resource tr "
            + "JOIN cleanup_flight cf ON tr.id = cf.tracked_resource_id "
            + "WHERE cf.flight_state = :flight_state LIMIT :limit";
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

  /** Retrieve the {@link CleanupFlight}s associated with the tracked resource. */
  public List<CleanupFlight> getFlights(TrackedResourceId trackedResourceId) {
    String sql =
        "SELECT flight_id, flight_state from cleanup_flight WHERE tracked_resource_id = :tracked_resource_id;";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("tracked_resource_id", trackedResourceId.uuid());
    return jdbcTemplate.query(sql, params, CLEANUP_FLIGHT_ROW_MAPPER);
  }

  /** Modifies the {@link CleanupFlightState} of a single flight. */
  public void setFlightState(String flightId, CleanupFlightState flightState) {
    String sql =
        "UPDATE cleanup_flight SET flight_state = :flight_state " + "WHERE flight_id = :flight_id;";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("flight_state", flightState.toString())
            .addValue("flight_id", flightId);
    jdbcTemplate.update(sql, params);
  }

  private static final RowMapper<TrackedResource> TRACKED_RESOURCE_ROW_MAPPER =
      (rs, rowNum) ->
          TrackedResource.builder()
              .trackedResourceId(TrackedResourceId.create(rs.getObject("id", UUID.class)))
              .cloudResourceUid(deserialize(rs.getString("resource_uid")))
              .creation(rs.getObject("creation", OffsetDateTime.class).toInstant())
              .expiration(rs.getObject("expiration", OffsetDateTime.class).toInstant())
              .trackedResourceState(TrackedResourceState.valueOf(rs.getString("state")))
              .build();

  private static final RowMapper<CleanupFlight> CLEANUP_FLIGHT_ROW_MAPPER =
      (rs, rowNum) ->
          CleanupFlight.create(
              rs.getString("flight_id"), CleanupFlightState.valueOf(rs.getString("flight_state")));

  /**
   * Serializes {@link CloudResourceUid} into json format string.
   *
   * <p>It only contains non null fields and should not be changed since this is how the database
   * will store {@link CloudResourceUid} in json format.
   */
  @VisibleForTesting
  static String serialize(CloudResourceUid resource) {
    ObjectMapper mapper =
        new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    try {
      return mapper.writeValueAsString(resource);
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
