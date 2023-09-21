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
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.*;

import static org.springframework.util.MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;

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
  @Builder.Default
  private List<Domain> domains = new ArrayList<>();
  @Builder.Default
  private List<Enrichment> enrichments = new ArrayList<>();

  private static List<ActionType> DATA_AMENDED_TYPES = List.of(
          ActionType.INGRESS,
          ActionType.TRANSFORM,
          ActionType.LOAD);

  boolean queued() { return state == ActionState.QUEUED || state == ActionState.COLD_QUEUED; }

  boolean terminal() {
    return !queued();
  }

  boolean complete() {
    return state == ActionState.COMPLETE;
  }

  public boolean amendedData() {
    return (state == ActionState.COMPLETE || state == ActionState.RETRIED) && DATA_AMENDED_TYPES.contains(type);
  }

  public boolean afterFormat() {
    return type == ActionType.VALIDATE || type == ActionType.EGRESS;
  }

  public void addDomain(@NotNull String domainKey, String domainValue, @NotNull String mediaType) {
    Optional<Domain> domain = getDomains().stream().filter(d -> d.getName().equals(domainKey)).findFirst();
    if (domain.isPresent()) {
      domain.get().setValue(domainValue);
    } else {
      getDomains().add(new Domain(domainKey, domainValue, mediaType));
    }
  }

  public void addEnrichment(@NotNull String enrichmentKey, String enrichmentValue) {
    addEnrichment(enrichmentKey, enrichmentValue, APPLICATION_OCTET_STREAM_VALUE);
  }

  public void addEnrichment(@NotNull String enrichmentKey, String enrichmentValue, @NotNull String mediaType) {
    Optional<Enrichment> enrichment = getEnrichments().stream().filter(d -> d.getName().equals(enrichmentKey)).findFirst();
    if (enrichment.isPresent()) {
      enrichment.get().setValue(enrichmentValue);
    } else {
      getEnrichments().add(new Enrichment(enrichmentKey, enrichmentValue, mediaType));
    }
  }
}
