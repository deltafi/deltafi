/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.types;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.types.ActionState;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.ResumeMetadata;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Action {
  static final private int MAX_CAUSE_SIZE = 100_000;

  @JsonProperty("n")
  @JsonAlias("name")
  private String name;
  @JsonProperty("ac")
  @JsonAlias("actionClass")
  private String actionClass;
  @Builder.Default
  @JsonProperty("t")
  @JsonAlias("type")
  private ActionType type = ActionType.UNKNOWN;
  @JsonProperty("s")
  @JsonAlias("state")
  private ActionState state;
  @JsonProperty("c")
  @JsonAlias("created")
  private OffsetDateTime created;
  @JsonProperty("q")
  @JsonAlias("queued")
  private OffsetDateTime queued;
  @JsonProperty("st")
  @JsonAlias("start")
  private OffsetDateTime start;
  @JsonProperty("sp")
  @JsonAlias("stop")
  private OffsetDateTime stop;
  @JsonProperty("m")
  @JsonAlias("modified")
  private OffsetDateTime modified;
  @JsonProperty("ec")
  @JsonAlias("errorCause")
  private String errorCause;
  @JsonProperty("ex")
  @JsonAlias("errorContext")
  @Builder.Default
  private String errorContext = "";
  @JsonProperty("nr")
  @JsonAlias("nextAutoResume")
  private OffsetDateTime nextAutoResume;
  @JsonProperty("nrr")
  @JsonAlias("nextAutoResumeReason")
  private String nextAutoResumeReason;
  @JsonProperty("fc")
  @JsonAlias("filteredCause")
  private String filteredCause;
  @JsonProperty("fx")
  @JsonAlias("filteredContext")
  private String filteredContext;
  @Builder.Default
  @JsonProperty("a")
  @JsonAlias("attempt")
  private int attempt = 1;
  @JsonProperty("ct")
  @JsonAlias("content")
  private List<Content> content;
  @Builder.Default
  @JsonProperty("md")
  @JsonAlias("metadata")
  private Map<String, String> metadata = new HashMap<>();
  @JsonProperty("dk")
  @JsonAlias("deleteMetadataKeys")
  private List<String> deleteMetadataKeys;
  @JsonProperty("rs")
  @JsonAlias("replayStart")
  private boolean replayStart; // marker for the starting point of a replay

  public Action(Action other) {
    this.name = other.name;
    this.actionClass = other.actionClass;
    this.type = other.type;
    this.state = other.state;
    this.created = other.created;
    this.queued = other.queued;
    this.start = other.start;
    this.stop = other.stop;
    this.modified = other.modified;
    this.errorCause = other.errorCause;
    this.errorContext = other.errorContext;
    this.nextAutoResume = other.nextAutoResume;
    this.nextAutoResumeReason = other.nextAutoResumeReason;
    this.filteredCause = other.filteredCause;
    this.filteredContext = other.filteredContext;
    this.attempt = other.attempt;
    this.content = other.content == null ? null : other.content;
    this.metadata = other.metadata == null ? null : new HashMap<>(other.metadata);
    this.deleteMetadataKeys = other.deleteMetadataKeys == null ? null : new ArrayList<>(other.deleteMetadataKeys);
    this.replayStart = other.replayStart;
  }

  public List<Content> getContent() {
      return content == null ? Collections.emptyList() : content;
  }

  public Map<String, String> getMetadata() {
      return metadata == null ? Collections.emptyMap() : metadata;
  }

  public List<String> getDeleteMetadataKeys() {
      return deleteMetadataKeys == null ? Collections.emptyList() : deleteMetadataKeys;
  }

  boolean queued() { return state == ActionState.QUEUED || state == ActionState.COLD_QUEUED; }

  boolean terminal() {
    return !queued() && state != ActionState.JOINING;
  }

  public void cancel(OffsetDateTime time) {
    if (terminal() && state != ActionState.ERROR) {
        return;
    }
    nextAutoResume = null;
    nextAutoResumeReason = null;
    state = ActionState.CANCELLED;
    modified = time;
  }

  public void setFilteredActionState(OffsetDateTime start, OffsetDateTime stop, OffsetDateTime now, String filteredCause, String filteredContext) {
    this.filteredCause = filteredCause == null ? "" : filteredCause.substring(0, Math.min(filteredCause.length(), MAX_CAUSE_SIZE));
    this.filteredContext = filteredContext == null ? "" : filteredContext.substring(0, Math.min(filteredContext.length(), MAX_CAUSE_SIZE));
    changeState(ActionState.FILTERED, start, stop, now);
  }

  public void changeState(ActionState actionState, OffsetDateTime start, OffsetDateTime stop, OffsetDateTime now) {
    setState(actionState);
    if (created == null) {
      created = now;
    }
    this.start = start;
    this.stop = stop;
    modified = now;
    setNextAutoResume(nextAutoResume);
  }

  public void complete(OffsetDateTime start, OffsetDateTime stop, List<Content> content, Map<String, String> metadata,
                               List<String> deleteMetadataKeys, OffsetDateTime now) {
    changeState(ActionState.COMPLETE, start, stop, now);

    if (content != null) {
      setContent(content);
    }

    if (metadata != null) {
      setMetadata(metadata);
    }

    if (deleteMetadataKeys != null) {
      setDeleteMetadataKeys(deleteMetadataKeys);
    }
  }

  public void retry(@NotNull List<ResumeMetadata> resumeMetadata, OffsetDateTime now) {
    state = ActionState.RETRIED;
    modified = now;
    nextAutoResume = null;
    nextAutoResumeReason = null;
    resumeMetadata
            .stream()
            .filter(this::metadataActionMatches)
            .forEach(this::updateResumeMetadata);
  }

  private void updateResumeMetadata(ResumeMetadata resumeMetadata) {
    if (resumeMetadata.getMetadata() != null) {
      setMetadata(KeyValueConverter.convertKeyValues(resumeMetadata.getMetadata()));
    }
    if (resumeMetadata.getDeleteMetadataKeys() != null) {
      setDeleteMetadataKeys(resumeMetadata.getDeleteMetadataKeys());
    }
  }

  private boolean metadataActionMatches(ResumeMetadata resumeMetadata) {
    return  resumeMetadata.getAction().equals(name);
  }

  public boolean acknowledgeError(OffsetDateTime now) {
    if (state == ActionState.ERROR) {
      modified = now;
      nextAutoResume = null;
      nextAutoResumeReason = null;
      return true;
    }
    return false;
  }

  public void error(OffsetDateTime start, OffsetDateTime stop, OffsetDateTime now, String cause, String context) {
    changeState(ActionState.ERROR, start, stop, now);
    errorCause = cause == null ? "" : cause.substring(0, Math.min(cause.length(), MAX_CAUSE_SIZE));
    errorContext = context == null ? "" : context.substring(0, Math.min(context.length(), MAX_CAUSE_SIZE));
  }

  public Action createChildAction() {
    Action childAction = new Action();
    childAction.setCreated(created);
    childAction.setModified(modified);
    childAction.setStart(start);
    childAction.setStop(stop);
    childAction.setName(name);
    childAction.setActionClass(actionClass);
    childAction.setType(type);
    childAction.setState(ActionState.INHERITED);
    childAction.setCreated(created);
    childAction.setQueued(queued);
    childAction.setAttempt(attempt);
    childAction.setReplayStart(replayStart);
    return childAction;
  }
}
