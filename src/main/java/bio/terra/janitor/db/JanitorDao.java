package bio.terra.janitor.db;

import static bio.terra.janitor.common.DbUtils.getUUIDField;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.janitor.app.configuration.JanitorJdbcConfiguration;
import bio.terra.janitor.common.CloudResourceType;
import bio.terra.janitor.common.exception.DuplicateLabelException;
import bio.terra.janitor.common.exception.DuplicateTrackedResourceException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JanitorDao {
  private static final Logger logger = LoggerFactory.getLogger(JanitorDao.class);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public JanitorDao(JanitorJdbcConfiguration jdbcConfiguration) {
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  /** Creates the tracked_resource record and adding labels. */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public UUID createResource(
      CloudResourceUid cloudResourceUid,
      CloudResourceType.Enum resourceType,
      Map<String, String> labels,
      @NotNull @Valid OffsetDateTime creation,
      OffsetDateTime expiration) {
    // TODO(yonghao): Solution for handling duplicate CloudResourceUid.
    String sql =
        "INSERT INTO tracked_resource (resource_uid, resource_type, creation, expirationTimeMills) values "
            + "(:resource_uid, :resource_type, :creation, :expirationTimeMills)";

    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("resource_uid", cloudResourceUid)
            .addValue("resource_type", resourceType)
            .addValue("creation", creation)
            .addValue("expirationTimeMills", expiration);

    try {
      jdbcTemplate.update(sql, params, keyHolder);
    } catch (DuplicateKeyException e) {
      throw new DuplicateTrackedResourceException(
          "tracked_resource " + cloudResourceUid + " already exists.", e);
    }

    UUID uuid = getUUIDField(keyHolder);

    String insertLabelSql =
        "INSERT INTO label (tracked_resource_id, key, value) values "
            + "(:tracked_resource_id, :key, :value)";

    for (Map.Entry<String, String> entry : labels.entrySet()) {
      GeneratedKeyHolder labelKeyHolder = new GeneratedKeyHolder();
      MapSqlParameterSource labelSqlParams =
          new MapSqlParameterSource()
              .addValue("tracked_resource_id", uuid)
              .addValue("key", entry.getKey())
              .addValue("value", entry.getValue());
      try {
        jdbcTemplate.update(insertLabelSql, labelSqlParams, labelKeyHolder);
      } catch (DuplicateKeyException e) {
        throw new DuplicateLabelException(
            "Label " + getUUIDField(labelKeyHolder) + " already exists.", e);
      }
    }

    return uuid;
  }
}
