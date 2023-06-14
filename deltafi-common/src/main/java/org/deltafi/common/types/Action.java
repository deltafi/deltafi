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

import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Action {
  private String name;
  @Builder.Default
  private ActionType type = ActionType.UNKNOWN;
  private String flow;
  private ActionState state;
  private OffsetDateTime created;
  private OffsetDateTime queued;
  private OffsetDateTime start;
  private OffsetDateTime stop;
  private OffsetDateTime modified;
  private String errorCause;
  private String errorContext;
  private String filteredCause;
  @Builder.Default
  private int attempt = 1;
  @Builder.Default
  private List<Content> content = new ArrayList<>();
  @Builder.Default
  private Map<String, String> metadata = new HashMap<>();
  @Builder.Default
  private List<String> deleteMetadataKeys = new ArrayList<>();

  private static List<ActionType> DATA_AMENDED_TYPES = List.of(
          ActionType.INGRESS,
          ActionType.TRANSFORM,
          ActionType.LOAD);

  boolean terminal() {
    return state != ActionState.QUEUED;
  }

  public boolean amendedData() {
    return state == ActionState.COMPLETE && DATA_AMENDED_TYPES.contains(type);
  }
}
