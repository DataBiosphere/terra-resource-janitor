package bio.terra.janitor.db;

import static bio.terra.janitor.common.JanitorObjectMapperHelper.serializeCloudResourceUid;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.common.JanitorObjectMapperHelper;
import bio.terra.janitor.common.ResourceType;
import bio.terra.janitor.common.ResourceTypeVisitor;
import bio.terra.janitor.common.exception.DuplicateLabelException;
import bio.terra.janitor.common.exception.DuplicateTrackedResourceException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
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

  /** Creates the tracked_resource record and adding labels. */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public UUID createResource(
      CloudResourceUid cloudResourceUid,
      Map<String, String> labels,
      Instant creation,
      Instant expiration) {
    // TODO(yonghao): Solution for handling duplicate CloudResourceUid.
    String sql =
        "INSERT INTO tracked_resource (id, resource_uid, resource_type, creation, expiration, state) values "
            + "(:id, :resource_uid::jsonb, :resource_type, :creation, :expiration, :state)";

    UUID trackedResourceId = UUID.randomUUID();
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", trackedResourceId)
            .addValue("resource_uid", serializeCloudResourceUid(cloudResourceUid))
            .addValue(
                "resource_type", new ResourceTypeVisitor().accept(cloudResourceUid).toString())
            .addValue("creation", creation.atOffset(ZoneOffset.UTC))
            .addValue("state", "READY")
            .addValue("expiration", expiration.atOffset(ZoneOffset.UTC));

    try {
      jdbcTemplate.update(sql, params);

    } catch (DuplicateKeyException e) {
      throw new DuplicateTrackedResourceException(
          "tracked_resource " + cloudResourceUid + " already exists.", e);
    }

    if (labels != null) {
      String insertLabelSql =
          "INSERT INTO label (tracked_resource_id, key, value) values "
              + "(:tracked_resource_id, :key, :value)";

      MapSqlParameterSource[] sqlParameterSourceList =
          labels.entrySet().stream()
              .map(
                  entry ->
                      new MapSqlParameterSource()
                          .addValue("tracked_resource_id", trackedResourceId)
                          .addValue("key", entry.getKey())
                          .addValue("value", entry.getValue()))
              .toArray(size -> new MapSqlParameterSource[labels.size()]);
      try {
        jdbcTemplate.batchUpdate(insertLabelSql, sqlParameterSourceList);
      } catch (DuplicateKeyException e) {
        throw new DuplicateLabelException(
            "Duplicate label found for tracked_resource_id: "
                + trackedResourceId
                + " already exists.",
            e);
      }
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
  public void initiateFlight(UUID trackedResourceId, String flightId) {
    {
      String sql = "UPDATE tracked_resource SET state = :cleaning WHERE state = :ready id = :id;";
      MapSqlParameterSource params =
          new MapSqlParameterSource()
              .addValue("cleaning", TrackedResourceState.CLEANING.toString())
              .addValue("ready", TrackedResourceState.READY.toString())
              .addValue("id", trackedResourceId);
      jdbcTemplate.update(sql, params);
    }
    {
      String sql =
          "INSERT INTO flight (tracked_resource_id, flight_id, state) values (:tracked_resource_id, :flight_id, :initiating)";
      MapSqlParameterSource params =
          new MapSqlParameterSource()
              .addValue("tracked_resource_id", trackedResourceId)
              .addValue("flight_id", flightId)
              .addValue("initiating", FlightState.INITIATING);
      jdbcTemplate.update(sql, params);
    }
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void setFlightState(String flightId, FlightState flightState) {
    String sql = "UPDATE flight SET state = :state WHERE flight_id = :flight_id;";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("cleaning", TrackedResourceState.CLEANING.toString())
            .addValue("ready", TrackedResourceState.READY.toString())
            .addValue("id", trackedResourceId);
    jdbcTemplate.update(sql, params);
  }

  private static final RowMapper<TrackedResource> TRACKED_RESOURCE_ROW_MAPPER =
      new RowMapper<TrackedResource>() {
        @Override
        public TrackedResource mapRow(ResultSet rs, int rowNum) throws SQLException {
          return TrackedResource.builder()
              .id(rs.getObject("id", UUID.class))
              .resourceType(ResourceType.valueOf(rs.getString("resource_type")))
              .cloudResourceUid(JanitorObjectMapperHelper.deserialize(rs.getString("resource_uid")))
              .creationTime(rs.getObject("creation", OffsetDateTime.class).toInstant())
              .expirationTime(rs.getObject("expiration", OffsetDateTime.class).toInstant())
              .trackedResourceState(TrackedResourceState.valueOf(rs.getString("state")))
              .build();
        }
      };
}
