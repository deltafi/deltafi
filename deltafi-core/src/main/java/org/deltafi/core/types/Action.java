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
package org.deltafi.core.types;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.uuid.Generators;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.types.ActionState;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.ResumeMetadata;
import org.hibernate.annotations.Type;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.*;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "actions", indexes = {
        @Index(name = "idx_action", columnList = "delta_file_flow_id, state, type, name, next_auto_resume, error_acknowledged"),
        @Index(name = "idx_action_state", columnList = "state, delta_file_flow_id")
})
@EqualsAndHashCode(exclude = "deltaFileFlow")
public class Action {
  static final private int MAX_CAUSE_SIZE = 100_000;

  @Id
  @Builder.Default
  private UUID id = Generators.timeBasedEpochGenerator().generate();
  private String name;
  private int number;
  @Builder.Default
  @Enumerated(EnumType.STRING)
  private ActionType type = ActionType.UNKNOWN;
  @Enumerated(EnumType.STRING)
  private ActionState state;
  private OffsetDateTime created;
  private OffsetDateTime queued;
  private OffsetDateTime start;
  private OffsetDateTime stop;
  private OffsetDateTime modified;
  @Column(length = MAX_CAUSE_SIZE)
  private String errorCause;
  @Column(length = MAX_CAUSE_SIZE)
  private String errorContext;
  private OffsetDateTime errorAcknowledged;
  private String errorAcknowledgedReason;
  private OffsetDateTime nextAutoResume;
  private String nextAutoResumeReason;
  @Column(length = MAX_CAUSE_SIZE)
  private String filteredCause;
  @Column(length = MAX_CAUSE_SIZE)
  private String filteredContext;
  @Builder.Default
  private int attempt = 1;
  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private List<Content> content;
  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @Builder.Default
  private Map<String, String> metadata = new HashMap<>();
  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private List<String> deleteMetadataKeys;
  private boolean replayStart; // marker for the starting point of a replay

  @Version
  private int version;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "delta_file_flow_id", foreignKey = @ForeignKey(NO_CONSTRAINT))
  @ToString.Exclude
  @JsonBackReference
  private DeltaFileFlow deltaFileFlow;

  public Action(Action other) {
    this.id = other.id;
    this.name = other.name;
    this.number = other.number;
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
    this.content = other.content == null ? null : other.content;
    this.metadata = other.metadata == null ? null : new HashMap<>(other.metadata);
    this.deleteMetadataKeys = other.deleteMetadataKeys == null ? null : new ArrayList<>(other.deleteMetadataKeys);
    this.replayStart = other.replayStart;
    this.deltaFileFlow = other.deltaFileFlow;
    this.version = other.version;
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
    errorCause = cause == null ? "" : cause.substring(0, Math.min(cause.length(), MAX_CAUSE_SIZE));
    errorContext = context == null ? "" : context.substring(0, Math.min(context.length(), MAX_CAUSE_SIZE));
  }

  public Action createChildAction() {
    Action childAction = new Action();
    childAction.setNumber(number);
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
