package bio.terra.janitor.db;

import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.common.exception.InvalidResourceUidException;
import bio.terra.janitor.generated.model.CloudResourceUid;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.ResultSetExtractor;
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
  /** The labels key used to distinguish janitor clients. */
  private static final String CLIENT_LABEL_KEY = "client";

  /**
   * This mapper must stay constant over time to ensure that older versions of obvious can be read.
   * Change here must be accompanied by an upgrade process to ensure that all data is rewritten in
   * the new form.
   */
  private static final ObjectMapper SERDES_MAPPER =
      new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_ABSENT);

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
        "INSERT INTO tracked_resource (id, resource_uid, resource_type, creation, expiration, state, metadata) values "
            + "(:id, :resource_uid::jsonb, :resource_type, :creation, :expiration, :state, :metadata::jsonb)";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", resource.trackedResourceId().uuid())
            .addValue("resource_uid", serialize(resource.cloudResourceUid()))
            .addValue(
                "resource_type",
                new ResourceTypeVisitor().accept(resource.cloudResourceUid()).toString())
            .addValue("creation", resource.creation().atOffset(ZoneOffset.UTC))
            .addValue("state", resource.trackedResourceState().toString())
            .addValue("expiration", resource.expiration().atOffset(ZoneOffset.UTC))
            .addValue("metadata", serialize(resource.metadata()));

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

  /** Returns the {@link TrackedResource} for a {@link TrackedResourceId} if there is one. */
  @Transactional(propagation = Propagation.SUPPORTS)
  public Optional<TrackedResource> retrieveTrackedResource(TrackedResourceId trackedResourceId) {
    String sql =
        "SELECT id, resource_uid, creation, expiration, state, metadata FROM tracked_resource tr "
            + "WHERE id = :id";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", trackedResourceId.uuid());
    return Optional.ofNullable(
        DataAccessUtils.singleResult(jdbcTemplate.query(sql, params, TRACKED_RESOURCE_ROW_MAPPER)));
  }

  /**
   * Modifies the {@link TrackedResourceState} for a single id. Returns the updated TrackedResource,
   * if one was updated.
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public Optional<TrackedResource> updateResourceState(
      TrackedResourceId trackedResourceId, TrackedResourceState newState) {
    String sql =
        "UPDATE tracked_resource SET state = :state WHERE id = :id "
            + "RETURNING id, resource_uid, creation, expiration, state, metadata";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("state", newState.toString())
            .addValue("id", trackedResourceId.uuid());
    return Optional.ofNullable(
        DataAccessUtils.singleResult(jdbcTemplate.query(sql, params, TRACKED_RESOURCE_ROW_MAPPER)));
  }

  /** Returns the tracked reosurces matching the {@code filter}. */
  @Transactional(propagation = Propagation.SUPPORTS)
  public List<TrackedResource> retrieveResourcesMatching(TrackedResourceFilter filter) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT id, resource_uid, creation, expiration, state, metadata FROM tracked_resource ");
    MapSqlParameterSource params = new MapSqlParameterSource();
    addFilterClauses(filter, sql, params);
    return jdbcTemplate.query(sql.toString(), params, TRACKED_RESOURCE_ROW_MAPPER);
  }

  /**
   * Modify the input {code sql} and {@code params} to append the clauses and parameters for {@code
   * filter}.
   */
  private static void addFilterClauses(
      TrackedResourceFilter filter, StringBuilder sql, MapSqlParameterSource params) {
    List<String> whereClauses = new ArrayList<>();
    if (!filter.allowedStates().isEmpty()) {
      whereClauses.add("state IN(:filter_allowed_states)");
      params.addValue(
          "filter_allowed_states",
          filter.allowedStates().stream()
              .map(TrackedResourceState::toString)
              .collect(Collectors.toList()));
    }
    if (!filter.forbiddenStates().isEmpty()) {
      whereClauses.add("state NOT IN(:filter_forbidden_states)");
      params.addValue(
          "filter_forbidden_states",
          filter.forbiddenStates().stream()
              .map(TrackedResourceState::toString)
              .collect(Collectors.toList()));
    }
    if (filter.cloudResourceUid().isPresent()) {
      whereClauses.add("resource_uid = :filter_cloud_resource_uid::jsonb");
      params.addValue("filter_cloud_resource_uid", serialize(filter.cloudResourceUid().get()));
    }
    if (filter.expiredBy().isPresent()) {
      whereClauses.add("expiration <= :filters_expired_by");
      params.addValue("filters_expired_by", filter.expiredBy().get().atOffset(ZoneOffset.UTC));
    }
    if (!whereClauses.isEmpty()) {
      sql.append(whereClauses.stream().collect(Collectors.joining(" AND ", " WHERE ", "")));
    }
    if (filter.limit().isPresent() && filter.limit().getAsInt() > 0) {
      sql.append(" LIMIT :filter_limit ");
      params.addValue("filter_limit", filter.limit().getAsInt());
    }
    if (filter.offset().isPresent() && filter.offset().getAsInt() > 0) {
      sql.append(" OFFSET :offset ");
      params.addValue("offset", filter.offset().getAsInt());
    }
  }

  /** Return the resource and flight associated with the {@code flightId}, if they exist. */
  @Transactional(propagation = Propagation.SUPPORTS)
  public Optional<TrackedResourceAndFlight> retrieveResourceAndFlight(String flightId) {
    String sql =
        "SELECT tr.id, tr.resource_uid, tr.creation, tr.expiration, tr.state, tr.metadata, "
            + "cf.flight_id, cf.flight_state FROM tracked_resource tr "
            + "JOIN cleanup_flight cf ON tr.id = cf.tracked_resource_id "
            + "WHERE cf.flight_id = :flight_id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("flight_id", flightId);
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) ->
                    TrackedResourceAndFlight.create(
                        TRACKED_RESOURCE_ROW_MAPPER.mapRow(rs, rowNum),
                        CLEANUP_FLIGHT_ROW_MAPPER.mapRow(rs, rowNum)))));
  }

  /**
   * Return the resource and labels associated with the {@code trackedResourceId}, if they exist.
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public Optional<TrackedResourceAndLabels> retrieveResourceAndLabels(
      TrackedResourceId trackedResourceId) {
    String sql =
        "SELECT tr.id, tr.resource_uid, tr.creation, tr.expiration, tr.state, tr.metadata, "
            + "l.key, l.value FROM tracked_resource tr "
            + "LEFT JOIN label l ON tr.id = l.tracked_resource_id "
            + "WHERE tr.id = :id";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", trackedResourceId.uuid());
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(sql, params, new TrackedResourceAndLabelsExtractor())));
  }

  /** Returns up to {@code limit} resources with a cleanup flight in the given state. */
  @Transactional(propagation = Propagation.SUPPORTS)
  public List<TrackedResourceAndFlight> retrieveResourcesWith(
      CleanupFlightState flightState, int limit) {
    String sql =
        "SELECT tr.id, tr.resource_uid, tr.creation, tr.expiration, tr.state, tr.metadata, "
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

  /** Returns {@link TrackedResourceAndLabels} matching {@link TrackedResourceFilter}. */
  @Transactional(propagation = Propagation.SUPPORTS)
  public List<TrackedResourceAndLabels> retrieveResourcesAndLabels(TrackedResourceFilter filter) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT tr.id, tr.resource_uid, tr.creation, tr.expiration, tr.state, tr.metadata, "
                + "l.key, l.value FROM tracked_resource tr "
                + "LEFT JOIN label l ON tr.id = l.tracked_resource_id ");
    MapSqlParameterSource params = new MapSqlParameterSource();
    addFilterClauses(filter, sql, params);
    return jdbcTemplate.query(sql.toString(), params, new TrackedResourceAndLabelsExtractor());
  }

  /** Creates a {@link CleanupFlight} associated with {@code trackedResourceId}. */
  @Transactional(propagation = Propagation.SUPPORTS)
  public void createCleanupFlight(
      TrackedResourceId trackedResourceId, CleanupFlight cleanupFlight) {
    jdbcTemplate.update(
        "INSERT INTO cleanup_flight (tracked_resource_id, flight_id, flight_state) "
            + "VALUES (:tracked_resource_id, :flight_id, :flight_state)",
        new MapSqlParameterSource()
            .addValue("tracked_resource_id", trackedResourceId.uuid())
            .addValue("flight_id", cleanupFlight.flightId())
            .addValue("flight_state", cleanupFlight.state().toString()));
  }

  /** Retrieve the {@link CleanupFlight}s associated with the tracked resource. */
  @Transactional(propagation = Propagation.SUPPORTS)
  public List<CleanupFlight> retrieveFlights(TrackedResourceId trackedResourceId) {
    String sql =
        "SELECT flight_id, flight_state from cleanup_flight WHERE tracked_resource_id = :tracked_resource_id;";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("tracked_resource_id", trackedResourceId.uuid());
    return jdbcTemplate.query(sql, params, CLEANUP_FLIGHT_ROW_MAPPER);
  }

  @Transactional(propagation = Propagation.SUPPORTS)
  public Optional<CleanupFlightState> retrieveFlightState(String flightId) {
    String sql = "SELECT flight_state from cleanup_flight WHERE flight_id = :flight_id;";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("flight_id", flightId);
    String rawState =
        DataAccessUtils.singleResult(jdbcTemplate.queryForList(sql, params, String.class));
    return Optional.ofNullable(rawState).map(CleanupFlightState::valueOf);
  }

  /**
   * Modifies the {@link CleanupFlightState} of a single flight. Returns the updated CleanupFlight,
   * if one was updated.
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public Optional<CleanupFlight> updateFlightState(
      String flightId, CleanupFlightState flightState) {
    String sql =
        "UPDATE cleanup_flight SET flight_state = :flight_state WHERE flight_id = :flight_id "
            + "RETURNING flight_id, flight_state";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("flight_state", flightState.toString())
            .addValue("flight_id", flightId);
    return Optional.ofNullable(
        DataAccessUtils.singleResult(jdbcTemplate.query(sql, params, CLEANUP_FLIGHT_ROW_MAPPER)));
  }

  @Transactional(propagation = Propagation.SUPPORTS)
  public Map<String, String> retrieveLabels(TrackedResourceId trackedResourceId) {
    String sql =
        "SELECT key, value FROM label " + "WHERE tracked_resource_id = :tracked_resource_id";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("tracked_resource_id", trackedResourceId.uuid());
    List<AbstractMap.SimpleEntry<String, String>> labels =
        jdbcTemplate.query(
            sql,
            params,
            (rs, rowNum) ->
                new AbstractMap.SimpleEntry<>(rs.getString("key"), rs.getString("value")));
    return labels.stream()
        .collect(
            Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
  }

  /**
   * Retrieve a table for the counts of all of the kind/state combinations of tracked resources in
   * the database.
   */
  public Table<ResourceKind, TrackedResourceState, Integer> retrieveResourceCounts() {
    String sql =
        "SELECT count(*) as count, tr.state, tr.resource_type, "
            + "(SELECT value FROM label WHERE tracked_resource_id =  tr.id and key = :client_key) as client "
            + "FROM tracked_resource tr GROUP BY tr.state, tr.resource_type, client";
    return jdbcTemplate.query(
        sql,
        new MapSqlParameterSource().addValue("client_key", CLIENT_LABEL_KEY),
        rs -> {
          Table<ResourceKind, TrackedResourceState, Integer> counts = HashBasedTable.create();
          while (rs.next()) {
            ResourceKind kind =
                ResourceKind.create(
                    rs.getString("client") == null ? "" : rs.getString("client"),
                    ResourceType.valueOf(rs.getString("resource_type")));
            TrackedResourceState state = TrackedResourceState.valueOf(rs.getString("state"));
            int count = rs.getInt("count");
            counts.put(kind, state, count);
          }
          return counts;
        });
  }

  private static final RowMapper<TrackedResource> TRACKED_RESOURCE_ROW_MAPPER =
      (rs, rowNum) ->
          TrackedResource.builder()
              .trackedResourceId(TrackedResourceId.create(rs.getObject("id", UUID.class)))
              .cloudResourceUid(deserialize(rs.getString("resource_uid")))
              .creation(rs.getObject("creation", OffsetDateTime.class).toInstant())
              .expiration(rs.getObject("expiration", OffsetDateTime.class).toInstant())
              .trackedResourceState(TrackedResourceState.valueOf(rs.getString("state")))
              .metadata(deserializeMetadata(rs.getString("metadata")))
              .build();

  private static final RowMapper<CleanupFlight> CLEANUP_FLIGHT_ROW_MAPPER =
      (rs, rowNum) ->
          CleanupFlight.create(
              rs.getString("flight_id"), CleanupFlightState.valueOf(rs.getString("flight_state")));

  /**
   * A {@link ResultSetExtractor} for extracting the results of a join of the one resource to many
   * labels relationship.
   */
  private static class TrackedResourceAndLabelsExtractor
      implements ResultSetExtractor<List<TrackedResourceAndLabels>> {
    @Override
    public List<TrackedResourceAndLabels> extractData(ResultSet rs)
        throws SQLException, DataAccessException {
      Map<TrackedResourceId, TrackedResourceAndLabels.Builder> resources = new HashMap<>();
      int rowNum = 0;
      while (rs.next()) {
        TrackedResourceId id = TrackedResourceId.create(rs.getObject("id", UUID.class));
        TrackedResourceAndLabels.Builder resourceBuilder = resources.get(id);
        if (resourceBuilder == null) {
          resourceBuilder = TrackedResourceAndLabels.builder();
          resourceBuilder.trackedResource(TRACKED_RESOURCE_ROW_MAPPER.mapRow(rs, rowNum));
          resources.put(id, resourceBuilder);
        }
        String labelKey = rs.getString("key");
        String labelValue = rs.getString("value");
        if (labelKey != null) {
          // Label may be null from left join for a resource with no labels.
          resourceBuilder.labelsBuilder().put(labelKey, labelValue);
        }
        ++rowNum;
      }
      return resources.values().stream()
          .map(TrackedResourceAndLabels.Builder::build)
          .collect(Collectors.toList());
    }
  }

  /**
   * Serializes {@link CloudResourceUid} into json format string.
   *
   * <p>It only contains non null fields and should not be changed since this is how the database
   * will store {@link CloudResourceUid} in json format.
   */
  @VisibleForTesting
  static String serialize(CloudResourceUid resource) {
    try {
      return SERDES_MAPPER.writeValueAsString(resource);
    } catch (JsonProcessingException e) {
      throw new InvalidResourceUidException("Failed to serialize CloudResourceUid");
    }
  }

  @VisibleForTesting
  static CloudResourceUid deserialize(String resource) {
    try {
      return SERDES_MAPPER.readValue(resource, CloudResourceUid.class);
    } catch (JsonProcessingException e) {
      throw new InvalidResourceUidException("Failed to deserialize CloudResourceUid: " + resource);
    }
  }

  /**
   * Serializes {@link ResourceMetadata} into json format string.
   *
   * <p>It only contains non null fields and should not be changed since this is how the database
   * will store {@link CloudResourceUid} in json format.
   */
  @VisibleForTesting
  static @Nullable String serialize(ResourceMetadata metadata) {
    try {
      return SERDES_MAPPER.writeValueAsString(MetadataModelV1.from(metadata));
    } catch (JsonProcessingException e) {
      throw new InvalidResourceUidException("Failed to serialize ResourceMetadata");
    }
  }

  @VisibleForTesting
  static ResourceMetadata deserializeMetadata(@Nullable String resource) {
    if (resource == null) {
      // Allow existing entries without a metadata column to be deserialized.
      return ResourceMetadata.none();
    }
    try {
      return SERDES_MAPPER.readValue(resource, MetadataModelV1.class).toMetadata();
    } catch (JsonProcessingException e) {
      throw new InvalidResourceUidException("Failed to deserialize ResourceMetadata: " + resource);
    }
  }

  /**
   * POJO class to use for JSON serializing a {@link ResourceMetadata}. This adds the workspaceOwner
   * field to the MetadataModelV1 class, making it backwards (but not forwards) compatible with
   * version 1.
   */
  @VisibleForTesting
  static class MetadataModelV1 {
    /** Version marker to store in the db so that we can update the format later if we need to. */
    @JsonProperty final long version = 2;

    @JsonProperty @Nullable String googleProjectParent;
    @JsonProperty @Nullable String workspaceOwner;

    public static MetadataModelV1 from(ResourceMetadata metadata) {
      MetadataModelV1 model = new MetadataModelV1();
      model.googleProjectParent = metadata.googleProjectParent().orElse(null);
      model.workspaceOwner = metadata.workspaceOwner().orElse(null);
      return model;
    }

    public ResourceMetadata toMetadata() {
      return ResourceMetadata.builder()
          .googleProjectParent(Optional.ofNullable(googleProjectParent))
          .workspaceOwner(Optional.ofNullable(workspaceOwner))
          .build();
    }
  }
}
