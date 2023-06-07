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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(builderMethodName = "newBuilder")
public class ActionEvent {
  private String did;
  private String action;
  private OffsetDateTime start;
  private OffsetDateTime stop;
  private OffsetDateTime time;
  private ActionEventType type;
  private TransformEvent transform;
  private LoadEvent load;
  private List<LoadEvent> loadMany;
  private DomainEvent domain;
  private EnrichEvent enrich;
  private FormatEvent format;
  private List<FormatEvent> formatMany;
  private ErrorEvent error;
  private FilterEvent filter;
  private List<ReinjectEvent> reinject;
  private List<Metric> metrics;

  public boolean valid() {
    return switch (type) {
      case TRANSFORM -> transform != null;
      case LOAD -> load != null;
      case LOAD_MANY -> loadMany != null;
      case DOMAIN -> domain != null;
      case ENRICH -> enrich != null;
      case FORMAT -> format != null;
      case FORMAT_MANY -> formatMany != null;
      case ERROR -> error != null;
      case FILTER -> filter != null;
      case REINJECT -> reinject != null && !reinject.isEmpty();
      case VALIDATE, EGRESS -> true;
      default -> false;
    };
  }
}