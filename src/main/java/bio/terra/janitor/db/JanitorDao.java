package bio.terra.janitor.db;

import static bio.terra.janitor.common.JanitorObjectMapperHelper.serializeCloudResourceUid;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.common.ResourceTypeVisitor;
import bio.terra.janitor.common.exception.DuplicateLabelException;
import bio.terra.janitor.common.exception.DuplicateTrackedResourceException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
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
}
