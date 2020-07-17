package bio.terra.janitor.service.janitor;

import bio.terra.generated.model.CloudResourceUid;
import bio.terra.generated.model.CreateResourceRequestBody;
import bio.terra.generated.model.CreatedResource;
import bio.terra.janitor.common.ResourceTypeVisitor;
import bio.terra.janitor.common.exception.InvalidResourceUidException;
import bio.terra.janitor.db.JanitorDao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JanitorService {

  private final JanitorDao janitorDao;
  private final ResourceTypeVisitor visitor;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  public JanitorService(JanitorDao janitorDao, ResourceTypeVisitor visitor) {
    this.janitorDao = janitorDao;
    this.visitor = visitor;
  }

  public CreatedResource createResource(CreateResourceRequestBody body) {
    CloudResourceUid cloudResourceUid = body.getResourceUid();
    // Only include the not null field, or postgres will mess the order of multiple classes and it's
    // really hard to query by this column.

    try {
      return new CreatedResource()
          .id(
              janitorDao
                  .createResource(
                      new ObjectMapper().writeValueAsString(cloudResourceUid),
                      visitor.accept(cloudResourceUid),
                      body.getLabels(),
                      body.getCreation(),
                      body.getCreation().plusMinutes(body.getTimeToLiveInMinutes()))
                  .toString());
    } catch (JsonProcessingException e) {
      throw new InvalidResourceUidException(
          "Failed to serialized cloudResourceUid for" + cloudResourceUid);
    }
  }
}
