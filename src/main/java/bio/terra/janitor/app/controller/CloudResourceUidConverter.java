package bio.terra.janitor.app.controller;

import bio.terra.rbs.generated.model.CloudResourceUid;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** A {@link Converter} to help Spring convert {@link CloudResourceUid} query parameters. */
@Component
public class CloudResourceUidConverter implements Converter<String, CloudResourceUid> {

  @Autowired ObjectMapper objectMapper;

  @Override
  public CloudResourceUid convert(String source) {
    try {
      return objectMapper.readValue(source, CloudResourceUid.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Unable to convert to CloudResourceUid from String.", e);
    }
  }
}
