package bio.terra.janitor.db;

import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.common.JanitorResourceTypeEnum;
import bio.terra.janitor.common.exception.DuplicateLabelException;
import bio.terra.janitor.common.exception.DuplicateTrackedResourceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
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
      String cloudResourceUid,
      JanitorResourceTypeEnum resourceType,
      Map<String, String> labels,
      OffsetDateTime creation,
      OffsetDateTime expiration) {
    // TODO(yonghao): Solution for handling duplicate CloudResourceUid.
    String sql =
        "INSERT INTO tracked_resource (id, resource_uid, resource_type, creation, expiration, state) values "
            + "(:id, :resource_uid::jsonb, :resource_type, :creation, :expiration, :state)";

    UUID trackedResourceId = UUID.randomUUID();
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", trackedResourceId)
            .addValue("resource_uid", cloudResourceUid)
            .addValue("resource_type", resourceType.toString())
            .addValue("creation", creation)
            .addValue("state", "READY")
            .addValue("expiration", expiration);

    try {
      jdbcTemplate.update(sql, params);

    } catch (DuplicateKeyException e) {
      throw new DuplicateTrackedResourceException(
          "tracked_resource " + cloudResourceUid + " already exists.", e);
    }

    String insertLabelSql =
        "INSERT INTO label (id, tracked_resource_id, key, value) values "
            + "(:id, :tracked_resource_id, :key, :value)";

    if (labels != null) {
      for (Map.Entry<String, String> entry : labels.entrySet()) {
        UUID labelId = UUID.randomUUID();
        MapSqlParameterSource labelSqlParams =
            new MapSqlParameterSource()
                .addValue("id", labelId)
                .addValue("tracked_resource_id", trackedResourceId)
                .addValue("key", entry.getKey())
                .addValue("value", entry.getValue())
                .addValue("state", "READY");
        try {
          jdbcTemplate.update(insertLabelSql, labelSqlParams);
        } catch (DuplicateKeyException e) {
          throw new DuplicateLabelException("Label " + labelId.toString() + " already exists.", e);
        }
      }
    }
    return trackedResourceId;
  }
}
