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
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.MediaType;
import java.time.OffsetDateTime;
import java.util.*;

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
  private List<Content> content;
  private Map<String, String> metadata;
  private List<String> deleteMetadataKeys;
  private List<Domain> domains;
  private List<Enrichment> enrichments;

  public Action(Action other) {
    this.name = other.name;
    this.type = other.type;
    this.flow = other.flow;
    this.state = other.state;
    this.created = other.created;
    this.queued = other.queued;
    this.start = other.start;
    this.stop = other.stop;
    this.modified = other.modified;
    this.errorCause = other.errorCause;
    this.errorContext = other.errorContext;
    this.filteredCause = other.filteredCause;
    this.attempt = other.attempt;
    this.content = other.content == null ? null : other.content.stream().map(Content::new).toList();
    this.metadata = other.metadata == null ? null : new HashMap<>(other.metadata);
    this.deleteMetadataKeys = other.deleteMetadataKeys == null ? null : new ArrayList<>(other.deleteMetadataKeys);
    this.domains = other.domains == null ? null : other.domains.stream().map(Domain::new).toList();
    this.enrichments = other.enrichments == null ? null : other.enrichments.stream().map(Enrichment::new).toList();
  }

  private static List<ActionType> DATA_AMENDED_TYPES = List.of(
          ActionType.INGRESS,
          ActionType.TRANSFORM,
          ActionType.LOAD);

  public List<Content> getContent() {
      return content == null ? Collections.emptyList() : content;
  }

  public Map<String, String> getMetadata() {
      return metadata == null ? Collections.emptyMap() : metadata;
  }

  public List<String> getDeleteMetadataKeys() {
      return deleteMetadataKeys == null ? Collections.emptyList() : deleteMetadataKeys;
  }

  public List<Domain> getDomains() {
      return domains == null ? Collections.emptyList() : domains;
  }

  public List<Enrichment> getEnrichments() {
      return enrichments == null ? Collections.emptyList() : enrichments;
  }

  boolean queued() { return state == ActionState.QUEUED || state == ActionState.COLD_QUEUED; }

  boolean terminal() {
    return !queued() && (state != ActionState.READY_TO_COLLECT) && (state != ActionState.COLLECTING);
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
    if (domains == null) {
      domains = new ArrayList<>();
    }
    Optional<Domain> domain = getDomains().stream().filter(d -> d.getName().equals(domainKey)).findFirst();
    if (domain.isPresent()) {
      domain.get().setValue(domainValue);
    } else {
      getDomains().add(new Domain(domainKey, domainValue, mediaType));
    }
  }

  public void addEnrichment(@NotNull String enrichmentKey, String enrichmentValue) {
    addEnrichment(enrichmentKey, enrichmentValue, MediaType.APPLICATION_OCTET_STREAM);
  }

  public void addEnrichment(@NotNull String enrichmentKey, String enrichmentValue, @NotNull String mediaType) {
    if (enrichments == null) {
      enrichments = new ArrayList<>();
    }
    Optional<Enrichment> enrichment = getEnrichments().stream().filter(d -> d.getName().equals(enrichmentKey)).findFirst();
    if (enrichment.isPresent()) {
      enrichment.get().setValue(enrichmentValue);
    } else {
      getEnrichments().add(new Enrichment(enrichmentKey, enrichmentValue, mediaType));
    }
  }
}
