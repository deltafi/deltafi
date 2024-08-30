/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.common.types;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionEvent {
  private UUID did;
  private String flowName;
  private Integer flowId;
  private String actionName;
  private Integer actionId;
  private OffsetDateTime start;
  private OffsetDateTime stop;
  private ActionEventType type;

  private List<TransformEvent> transform;
  private ErrorEvent error;
  private FilterEvent filter;
  private List<Metric> metrics;
  private IngressEvent ingress;

  private List<Content> savedContent;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public String validateHeader() {
    if (did == null) {
      return "Missing did";
    }
    if (StringUtils.isEmpty(flowName)) {
      return "Missing flowName";
    }
    if (flowId == null) {
      return "Missing flowId";
    }
    if (StringUtils.isEmpty(actionName)) {
      return "Missing actionName";
    }
    if (actionId == null) {
      return "Missing actionId";
    }

    return null;
  }

  public String validatePayload() {
    if (start == null) {
      return "Missing start";
    }
    if (stop == null) {
      return "Missing stop";
    }
    if (!typeValid()) {
      return "Action event type does not match the populated object";
    }
    return null;
  }

  private boolean typeValid() {
    return switch (type) {
      case TRANSFORM -> transform != null && !transform.isEmpty();
      case ERROR -> error != null;
      case FILTER -> filter != null;
      case EGRESS -> true;
      case INGRESS -> ingress != null && ingress.getIngressItems() != null;
      default -> false;
    };
  }

  @Override
  public String toString() {
    try {
      return OBJECT_MAPPER.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
