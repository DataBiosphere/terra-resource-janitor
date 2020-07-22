package bio.terra.janitor.db;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.common.ResourceTypeVisitor;
import bio.terra.janitor.common.exception.InvalidResourceUidException;
<<<<<<< HEAD
import bio.terra.janitor.db.exception.DuplicateDbKeyException;
=======
>>>>>>> master
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
<<<<<<< HEAD
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
=======
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
>>>>>>> master
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
<<<<<<< HEAD
  public String createResource(
=======
  public TrackedResourceId createResource(
>>>>>>> master
      CloudResourceUid cloudResourceUid,
      Map<String, String> labels,
      Instant creation,
      Instant expiration) {
    // TODO(yonghao): Solution for handling duplicate CloudResourceUid.
    String sql =
        "INSERT INTO tracked_resource (id, resource_uid, resource_type, creation, expiration, state) values "
            + "(:id, :resource_uid::jsonb, :resource_type, :creation, :expiration, :state)";

<<<<<<< HEAD
    TrackedResourceId trackedResourceId = new TrackedResourceId();
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", trackedResourceId.getUUID())
=======
    TrackedResourceId trackedResourceId = TrackedResourceId.create(UUID.randomUUID());

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", trackedResourceId.id())
>>>>>>> master
            .addValue("resource_uid", serialize(cloudResourceUid))
            .addValue(
                "resource_type", new ResourceTypeVisitor().accept(cloudResourceUid).toString())
            .addValue("creation", creation.atOffset(ZoneOffset.UTC))
            .addValue("state", "READY")
            .addValue("expiration", expiration.atOffset(ZoneOffset.UTC));

<<<<<<< HEAD
    try {
      jdbcTemplate.update(sql, params);

    } catch (DuplicateKeyException e) {
      throw new DuplicateDbKeyException(
          "tracked_resource " + cloudResourceUid + " already exists.", e);
    }

    if (labels != null) {
=======
    jdbcTemplate.update(sql, params);

    if (labels != null && !labels.isEmpty()) {
>>>>>>> master
      String insertLabelSql =
          "INSERT INTO label (tracked_resource_id, key, value) values "
              + "(:tracked_resource_id, :key, :value)";

      MapSqlParameterSource[] sqlParameterSourceList =
          labels.entrySet().stream()
              .map(
                  entry ->
                      new MapSqlParameterSource()
<<<<<<< HEAD
                          .addValue("tracked_resource_id", trackedResourceId.getUUID())
                          .addValue("key", entry.getKey())
                          .addValue("value", entry.getValue()))
              .toArray(size -> new MapSqlParameterSource[labels.size()]);
      try {
        jdbcTemplate.batchUpdate(insertLabelSql, sqlParameterSourceList);
      } catch (DuplicateKeyException e) {
        throw new DuplicateDbKeyException(
            "Duplicate label found for tracked_resource_id: "
                + trackedResourceId
                + " already exists.",
            e);
      }
    }
    return trackedResourceId.toString();
  }

  @VisibleForTesting
  static String serialize(CloudResourceUid resource) {
    ObjectMapper objectMapper =
        new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    try {
      return objectMapper.writeValueAsString(resource);
=======
                          .addValue("tracked_resource_id", trackedResourceId.id())
                          .addValue("key", entry.getKey())
                          .addValue("value", entry.getValue()))
              .toArray(size -> new MapSqlParameterSource[labels.size()]);

      jdbcTemplate.batchUpdate(insertLabelSql, sqlParameterSourceList);
    }
    return trackedResourceId;
  }

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
>>>>>>> master
    } catch (JsonProcessingException e) {
      throw new InvalidResourceUidException("Failed to serialize CloudResourceUid");
    }
  }
}
