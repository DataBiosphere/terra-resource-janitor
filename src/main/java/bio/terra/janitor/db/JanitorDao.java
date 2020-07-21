package bio.terra.janitor.db;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.common.ResourceTypeVisitor;
import bio.terra.janitor.common.exception.InvalidResourceUidException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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

  @VisibleForTesting
  /**
   * Serializes {@link CloudResourceUid} into json format and ignore null fields. This should not be
   * changed as this how {@link CloudResourceUid} represents in postgres.
   */
  static String serialize(CloudResourceUid resource) {
    ObjectMapper objectMapper =
        new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    try {
      return objectMapper.writeValueAsString(resource);
    } catch (JsonProcessingException e) {
      throw new InvalidResourceUidException("Failed to serialize CloudResourceUid");
    }
  }
}
