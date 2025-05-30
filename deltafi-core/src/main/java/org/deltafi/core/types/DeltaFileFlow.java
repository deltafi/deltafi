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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.uuid.Generators;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.*;
import org.deltafi.core.exceptions.UnexpectedActionException;
import org.deltafi.core.types.hibernate.StringArrayType;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "delta_file_flows")
@DynamicUpdate
@EqualsAndHashCode
public class DeltaFileFlow {
    @Id
    @Builder.Default
    private UUID id = Generators.timeBasedEpochGenerator().generate();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "flow_definition_id", nullable = false)
    private FlowDefinition flowDefinition;

    private int number;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "dff_state_enum")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private DeltaFileFlowState state = DeltaFileFlowState.IN_FLIGHT;
    private OffsetDateTime created;
    private OffsetDateTime modified;
    @Builder.Default
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private DeltaFileFlowInput input = new DeltaFileFlowInput();
    @Builder.Default
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<Action> actions = new ArrayList<>();
    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]")
    @Builder.Default
    private List<String> publishTopics = new ArrayList<>();
    private int depth;
    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]")
    @Builder.Default
    private List<String> pendingAnnotations = new ArrayList<>();
    boolean testMode;
    String testModeReason;
    private UUID joinId;
    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]")
    @Builder.Default
    private List<String> pendingActions = new ArrayList<>();
    private OffsetDateTime errorAcknowledged;
    private String errorAcknowledgedReason;
    private boolean coldQueued;
    private String errorOrFilterCause;
    private OffsetDateTime nextAutoResume;

    @Version
    @EqualsAndHashCode.Exclude
    private long version;

    @Transient
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    private DeltaFile owner;

    public DeltaFileFlow(DeltaFileFlow other) {
        this.id = other.id;
        this.flowDefinition = other.flowDefinition;
        this.number = other.number;
        this.state = other.state;
        this.created = other.created;
        this.modified = other.modified;
        this.input = other.input;
        this.actions = other.actions == null ? null : other.actions.stream().map(Action::new).collect(Collectors.toCollection(ArrayList::new));
        this.publishTopics = new ArrayList<>(other.publishTopics);
        this.depth = other.depth;
        this.pendingAnnotations = new ArrayList<>(other.pendingAnnotations);
        this.testMode = other.testMode;
        this.testModeReason = other.testModeReason;
        this.joinId = other.joinId;
        this.pendingActions = other.pendingActions;
        this.version = other.version;
        this.errorAcknowledged = other.errorAcknowledged;
        this.errorAcknowledgedReason = other.errorAcknowledgedReason;
        this.coldQueued = other.coldQueued;
        this.errorOrFilterCause = other.errorOrFilterCause;
        this.nextAutoResume = other.nextAutoResume;
        this.owner = other.owner;
    }

    public String getName() {
        return flowDefinition.getName();
    }

    public FlowType getType() {
        return flowDefinition.getType();
    }

    /**
     * Get the cumulative metadata from all actions in the dataSource
     *
     * @return A Map containing the resulting metadata
     */
    @Transient
    public Map<String, String> getMetadata() {
        if (number == 0) {
            Map<String, String> metadata = new HashMap<>();
            actions.forEach(a -> {
                metadata.putAll(a.getMetadata());
                a.getDeleteMetadataKeys().forEach(metadata::remove);
            });
            return metadata;
        }
        else if (owner == null) {
            throw new IllegalStateException("DeltaFileFlow missing pointer back to DeltaFile");
        }

        List<Integer> lineage = new ArrayList<>(input.getAncestorIds());
        lineage.add(number);
        return owner.metadataFor(lineage);
    }

    public List<Segment> uniqueSegments() {
        return actions.stream()
                .flatMap(a -> a.getContent().stream())
                .flatMap(c -> c.getSegments().stream())
                .distinct()
                .toList();
    }

    public void cancel(OffsetDateTime time) {
        if (state != DeltaFileFlowState.IN_FLIGHT && state != DeltaFileFlowState.ERROR) {
            return;
        }
        actions.forEach(a -> a.cancel(time));
        state = DeltaFileFlowState.CANCELLED;
        modified = time;
    }

    public boolean hasAutoResume() {
        return actions.stream().anyMatch(a -> a.getNextAutoResume() != null);
    }

    public List<Content> lastContent() {
        return latestMatchingAction(action -> action.getState() == ActionState.COMPLETE)
                .map(Action::getContent)
                .orElseGet(this::inputContent);
    }

    public long lastContentSize() {
        return lastContent().stream().map(Content::getSize).reduce(0L, Long::sum);
    }

    private List<Content> inputContent() {
        return input != null ? input.getContent() : List.of();
    }

    @Transient
    public List<Content> getImmutableContent() {
        return lastContent().stream().map(Content::copy).toList();
    }

    public Action firstAction() {
        if (actions.isEmpty()) {
            return null;
        }
        return actions.getFirst();
    }

    public Action lastAction() {
        if (actions.isEmpty()) {
            return null;
        }
        return actions.getLast();
    }

    public void enableAutoResume(OffsetDateTime nextResume, String nextResumeReason) {
        Action action = lastAction();
        action.setNextAutoResume(nextResume);
        action.setNextAutoResumeReason(nextResumeReason);
        setNextAutoResume(nextResume);
    }

    public boolean hasUnacknowledgedError() {
        Action lastAction = lastAction();
        return lastAction != null && lastAction.getState() == ActionState.ERROR && errorAcknowledged == null;
    }

    public boolean hasPendingAnnotations() {
        return pendingAnnotations != null && !pendingAnnotations.isEmpty();
    }

    /* Get the most recent action with the given name */
    public Optional<Action> actionNamed(String name) {
        return latestMatchingAction(action -> action.getName().equals(name));
    }

    public Optional<Action> lastCompleteAction() {
        return latestMatchingAction(action -> action.getState() == ActionState.COMPLETE);
    }

    public Optional<Action> latestMatchingAction(Predicate<Action> filter) {
        return getActions().stream()
                .filter(filter)
                .reduce((first, second) -> second);
    }

    public Action queueAction(String actionName, String actionClass, ActionType type, boolean coldQueue, OffsetDateTime now) {
        modified = now;
        return latestMatchingAction(action -> nameMatchesAndNotRetried(action, actionName))
                .map(action -> queueOldAction(action, coldQueue, now))
                .orElseGet(() -> queueNewAction(actionName, actionClass, type, coldQueue, now));
    }

    private boolean nameMatchesAndNotRetried(Action action, String actionName) {
        return action.getName().equals(actionName) && action.getState() != ActionState.RETRIED;
    }

    private Action queueOldAction(Action action, boolean coldQueue, OffsetDateTime now) {
        action.changeState(coldQueue ? ActionState.COLD_QUEUED : ActionState.QUEUED, null, null, now);
        return action;
    }

    public Action queueNewAction(String name, String actionClass, ActionType type, boolean coldQueue, OffsetDateTime now) {
        return addAction(name, actionClass, type, coldQueue ? ActionState.COLD_QUEUED : ActionState.QUEUED, now);
    }

    public Action addAction(String name, String actionClass, ActionType type, ActionState state, OffsetDateTime now) {
        Action action = Action.builder()
                .name(name)
                .actionClass(actionClass)
                .type(type)
                .state(state)
                .created(now)
                .queued(now)
                .modified(now)
                .attempt(1 + getLastAttemptNum(name))
                .build();
        if (actions == null) {
            actions = new ArrayList<>();
        }
        actions.add(action);
        return action;
    }

    private int getLastAttemptNum(String name) {
        Optional<Action> action = getActions().stream()
                .filter(a -> a.getName().equals(name) && a.getState() != ActionState.RETRIED)
                .reduce((first, second) -> second);
        return action.map(Action::getAttempt).orElse(0);
    }

    public List<String> queuedActions() {
        return getActions().stream().filter(Action::queued).map(Action::getName).toList();
    }

    public boolean terminal() {
        return state == DeltaFileFlowState.COMPLETE || state == DeltaFileFlowState.PENDING_ANNOTATIONS ||state == DeltaFileFlowState.CANCELLED || state == DeltaFileFlowState.ERROR || state == DeltaFileFlowState.FILTERED;
    }

    public Action getAction(String actionName) {
        return getActions().stream()
                .filter(action -> action.getName().equals(actionName))
                .findFirst()
                .orElse(null);
    }

    public Action getPendingAction(String actionName, UUID did) {
        Action action = lastAction();
        if (action == null || !action.getName().equals(actionName) || action.terminal()) {
            throw new UnexpectedActionException(flowDefinition.getName(), number, actionName, did, action == null ? "none" : action.getName());
        }

        return action;
    }

    public boolean resume(@NotNull List<ResumeMetadata> resumeMetadata, OffsetDateTime now) {
        Action lastAction = lastAction();
        if (lastAction == null || lastAction.getState() != ActionState.ERROR) {
            return false;
        }

        lastAction.retry(resumeMetadata.stream().filter(this::metadataFlowMatches).toList(), now);
        errorAcknowledged = null;
        errorAcknowledgedReason = null;
        updateState();
        return true;
    }

    private boolean metadataFlowMatches(ResumeMetadata resumeMetadata) {
        return resumeMetadata.getFlow().equals(flowDefinition.getName());
    }

    public boolean hasActionInState(ActionState actionState) {
        return actions.stream().anyMatch(action -> action.getState().equals(actionState));
    }

    public boolean acknowledgeError(OffsetDateTime now, String reason) {
        boolean acked = !actions.isEmpty() && actions.getLast().acknowledgeError(now);
        if (acked) {
            modified = now;
            errorAcknowledged = now;
            errorAcknowledgedReason = reason;
        }
        return acked;
    }

    public boolean hasFinalAction(String name) {
        return getActions().stream().anyMatch(action -> action.getName().equals(name) &&
                action.getState() != ActionState.RETRIED && action.terminal());
    }

    public void updateState() {
        Action action = lastAction();
        if (action == null) {
            state = DeltaFileFlowState.COMPLETE;
            nextAutoResume = null;
            errorOrFilterCause = null;
            return;
        }

        modified = action.getModified();
        ActionState lastState = action.getState();
        state = switch (lastState) {
            case null -> DeltaFileFlowState.COMPLETE;
            case ERROR -> DeltaFileFlowState.ERROR;
            case CANCELLED -> DeltaFileFlowState.CANCELLED;
            case COMPLETE -> hasPendingAnnotations() ? DeltaFileFlowState.PENDING_ANNOTATIONS : DeltaFileFlowState.COMPLETE;
            case JOINED, SPLIT -> DeltaFileFlowState.COMPLETE;
            case FILTERED -> DeltaFileFlowState.FILTERED;
            default -> DeltaFileFlowState.IN_FLIGHT;
        };
        coldQueued = lastState == ActionState.COLD_QUEUED;
        if (lastState == ActionState.ERROR) {
            errorOrFilterCause = action.getErrorCause();
            nextAutoResume = action.getNextAutoResume();
        } else {
            nextAutoResume = null;
            if (lastState == ActionState.FILTERED) {
                errorOrFilterCause = action.getFilteredCause();
            } else {
                errorOrFilterCause = null;
            }
        }
    }

    public void removePendingAnnotations(Set<String> receivedAnnotations) {
        this.pendingAnnotations = this.pendingAnnotations != null ? new ArrayList<>(pendingAnnotations) : new ArrayList<>();
        this.pendingAnnotations.removeAll(receivedAnnotations);
        updateState();
    }

    public void removePendingAction(String actionName) {
        if (!(pendingActions instanceof ArrayList<String>)) {
            pendingActions = new ArrayList<>(pendingActions);
        }
        pendingActions.remove(actionName);
    }

    @Transient
    public String getNextPendingAction() {
        return !pendingActions.isEmpty() ? pendingActions.getFirst() : null;
    }

    public ActionType lastActionType() {
        return !actions.isEmpty() ? lastAction().getType() : null;
    }

    public ActionState lastActionState() {
        return !actions.isEmpty() ? lastAction().getState() : null;
    }

    public List<Content> lastActionContent() {
        return !actions.isEmpty() ? lastAction().getContent() : List.of();
    }

    public void setPendingAnnotations(Set<String> expectedAnnotations) {
        this.pendingAnnotations = expectedAnnotations.stream().toList();
    }
}
