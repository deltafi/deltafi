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
import org.deltafi.common.converters.KeyValueConverter;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Action {
  private String name;
  private int id;
  @Builder.Default
  private ActionType type = ActionType.UNKNOWN;
  private ActionState state;
  private OffsetDateTime created;
  private OffsetDateTime queued;
  private OffsetDateTime start;
  private OffsetDateTime stop;
  private OffsetDateTime modified;
  private String errorCause;
  private String errorContext;
  private OffsetDateTime errorAcknowledged;
  private String errorAcknowledgedReason;
  private OffsetDateTime nextAutoResume;
  private String nextAutoResumeReason;
  private String filteredCause;
  private String filteredContext;
  @Builder.Default
  private int attempt = 1;
  private List<Content> content;
  private Map<String, String> metadata;
  private List<String> deleteMetadataKeys;
  private boolean replayStart; // marker for the starting point of a replay

  public Action(Action other) {
    this.name = other.name;
    this.id = other.id;
    this.type = other.type;
    this.state = other.state;
    this.created = other.created;
    this.queued = other.queued;
    this.start = other.start;
    this.stop = other.stop;
    this.modified = other.modified;
    this.errorCause = other.errorCause;
    this.errorContext = other.errorContext;
    this.errorAcknowledged = other.errorAcknowledged;
    this.errorAcknowledgedReason = other.errorAcknowledgedReason;
    this.nextAutoResume = other.nextAutoResume;
    this.nextAutoResumeReason = other.nextAutoResumeReason;
    this.filteredCause = other.filteredCause;
    this.filteredContext = other.filteredContext;
    this.attempt = other.attempt;
    this.content = other.content == null ? null : other.content.stream().map(Content::new).toList();
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

  boolean completeOrRetried() {
    return state == ActionState.COMPLETE || state == ActionState.RETRIED;
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

  public void changeState(ActionState actionState, OffsetDateTime start, OffsetDateTime stop, OffsetDateTime now) {
    changeState(actionState, start, stop, now, null, null, null);
  }

  public void setFilteredActionState(OffsetDateTime start, OffsetDateTime stop, OffsetDateTime now, String filteredCause, String filteredContext) {
    this.filteredCause = filteredCause;
    this.filteredContext = filteredContext;
    changeState(ActionState.FILTERED, start, stop, now);
  }

  private void changeState(ActionState actionState, OffsetDateTime start, OffsetDateTime stop, OffsetDateTime now, String errorCause, String errorContext, OffsetDateTime nextAutoResume) {
    setState(actionState);
    if (created == null) {
      created = now;
    }
    this.start = start;
    this.stop = stop;
    modified = now;
    this.errorCause = errorCause;
    this.errorContext = errorContext;
    this.nextAutoResume = nextAutoResume;
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
    errorAcknowledged = null;
    errorAcknowledgedReason = null;
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

  public boolean acknowledgeError(OffsetDateTime now, String reason) {
    if (state == ActionState.ERROR) {
      modified = now;
      errorAcknowledged = now;
      errorAcknowledgedReason = reason;
      nextAutoResume = null;
      nextAutoResumeReason = null;
      return true;
    }
    return false;
  }

  public void error(OffsetDateTime start, OffsetDateTime stop, OffsetDateTime now, String cause, String context) {
    changeState(ActionState.ERROR, start, stop, now);
    setErrorCause(cause);
    setErrorContext(context);
  }


  public boolean clearErrorAcknowledged(OffsetDateTime now) {
    if (state == ActionState.ERROR && errorAcknowledged != null) {
      modified = now;
      errorAcknowledged = null;
      errorAcknowledgedReason = null;
      return true;
    }
    return false;
  }

  public Action createChildAction() {
    Action childAction = new Action();
    childAction.setId(id);
    childAction.setCreated(created);
    childAction.setModified(modified);
    childAction.setStart(start);
    childAction.setStop(stop);
    childAction.setName(name);
    childAction.setType(type);
    childAction.setState(ActionState.INHERITED);
    childAction.setCreated(created);
    childAction.setQueued(queued);
    childAction.setAttempt(attempt);
    childAction.setReplayStart(replayStart);
    return childAction;
  }
}
