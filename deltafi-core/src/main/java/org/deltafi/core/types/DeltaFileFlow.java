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

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.uuid.Generators;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.*;
import org.deltafi.core.exceptions.UnexpectedActionException;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Type;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Predicate;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "delta_file_flows", indexes = {
        @Index(name = "idx_flow", columnList = "delta_file_id, state, name, test_mode, pending_annotations"),
        @Index(name = "idx_flow_state", columnList = "state, delta_file_id")
})
@NamedEntityGraph(
        name = "deltaFileFlow.withActions",
        attributeNodes = {
                @NamedAttributeNode("actions")
        }
)
@DynamicUpdate
public class DeltaFileFlow {
    @Id
    @Builder.Default
    private UUID id = Generators.timeBasedEpochGenerator().generate();

    private String name;
    private int number;
    @Enumerated(EnumType.STRING)
    private FlowType type;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private DeltaFileFlowState state = DeltaFileFlowState.IN_FLIGHT;
    private OffsetDateTime created;
    private OffsetDateTime modified;
    @Builder.Default
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private FlowPlanCoordinates flowPlan = new FlowPlanCoordinates();
    @Builder.Default
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private DeltaFileFlowInput input = new DeltaFileFlowInput();
    @OneToMany(mappedBy = "deltaFileFlow", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @Builder.Default
    @JsonManagedReference
    @OrderBy("number ASC")
    private List<Action> actions = new ArrayList<>();
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> publishTopics = new ArrayList<>();
    private int depth;
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Set<String> pendingAnnotations = new HashSet<>();
    boolean testMode;
    String testModeReason;
    private UUID joinId;
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> pendingActions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delta_file_id", foreignKey = @ForeignKey(NO_CONSTRAINT))
    @ToString.Exclude
    @JsonBackReference
    private DeltaFile deltaFile;

    @Version
    private long version;

    public DeltaFileFlow(DeltaFileFlow other) {
        this.id = other.id;
        this.name = other.name;
        this.number = other.number;
        this.type = other.type;
        this.state = other.state;
        this.created = other.created;
        this.modified = other.modified;
        this.flowPlan = new FlowPlanCoordinates(other.flowPlan);
        this.input = other.input;
        this.actions = other.actions == null ? null : other.actions.stream().map(Action::new).toList();
        this.publishTopics = new ArrayList<>(other.publishTopics);
        this.depth = other.depth;
        this.pendingAnnotations = new HashSet<>(other.pendingAnnotations);
        this.testMode = other.testMode;
        this.testModeReason = other.testModeReason;
        this.joinId = other.joinId;
        this.pendingActions = other.pendingActions;
        this.deltaFile = other.deltaFile;
        this.version = other.version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DeltaFileFlow other = (DeltaFileFlow) o;

        return number == other.number &&
                depth == other.depth &&
                testMode == other.testMode &&
                Objects.equals(id, other.id) &&
                Objects.equals(name, other.name) &&
                type == other.type &&
                state == other.state &&
                Objects.equals(created, other.created) &&
                Objects.equals(modified, other.modified) &&
                Objects.equals(flowPlan, other.flowPlan) &&
                Objects.equals(input, other.input) &&
                Objects.equals(new ArrayList<>(actions), new ArrayList<>(other.actions)) &&
                Objects.equals(publishTopics, other.publishTopics) &&
                Objects.equals(pendingAnnotations, other.pendingAnnotations) &&
                Objects.equals(testModeReason, other.testModeReason) &&
                Objects.equals(joinId, other.joinId) &&
                Objects.equals(pendingActions, other.pendingActions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, number, type, state, created, modified, flowPlan, input, new ArrayList<>(actions), publishTopics, depth, pendingAnnotations, testMode, testModeReason, joinId, pendingActions);
    }

    /**
     * Get the cumulative metadata from all actions in the flow
     *
     * @return A Map containing the resulting metadata
     */
    @Transient
    public Map<String, String> getMetadata() {
        Map<String, String> metadata = new HashMap<>(input.getMetadata());
        for (Action action : actions) {
            metadata.putAll(action.getMetadata());
            for (String key : action.getDeleteMetadataKeys()) {
                metadata.remove(key);
            }
        }

        return metadata;
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

    private List<Content> inputContent() {
        return input != null ? input.content : List.of();
    }

    @Transient
    public List<Content> getImmutableContent() {
        return lastContent().stream().map(Content::copy).toList();
    }

    public Action lastAction() {
        return actions.isEmpty() ? null : actions.getLast();
    }

    public boolean hasUnacknowledgedError() {
        Action lastAction = lastAction();
        return lastAction != null && lastAction.getState() == ActionState.ERROR && lastAction.getErrorAcknowledged() == null;
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

    public Action queueAction(String actionName, ActionType type, boolean coldQueue, OffsetDateTime now) {
        modified = now;
        return latestMatchingAction(action -> nameMatchesAndNotRetried(action, actionName))
                .map(action -> queueOldAction(action, coldQueue, now))
                .orElseGet(() -> queueNewAction(actionName, type, coldQueue, now));
    }

    private boolean nameMatchesAndNotRetried(Action action, String actionName) {
        return action.getName().equals(actionName) && action.getState() != ActionState.RETRIED;
    }

    private Action queueOldAction(Action action, boolean coldQueue, OffsetDateTime now) {
        action.changeState(coldQueue ? ActionState.COLD_QUEUED : ActionState.QUEUED, null, null, now);
        return action;
    }

    public Action queueNewAction(String name, ActionType type, boolean coldQueue, OffsetDateTime now) {
        return addAction(name, type, coldQueue ? ActionState.COLD_QUEUED : ActionState.QUEUED, now);
    }

    public Action addAction(String name, ActionType type, ActionState state, OffsetDateTime now) {
        Action action = Action.builder()
                .name(name)
                .number(actions.size())
                .type(type)
                .state(state)
                .created(now)
                .queued(now)
                .modified(now)
                .attempt(1 + getLastAttemptNum(name))
                .deltaFileFlow(this)
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
        return state == DeltaFileFlowState.COMPLETE || state == DeltaFileFlowState.CANCELLED || state == DeltaFileFlowState.ERROR;
    }

    public Action getAction(String actionName, UUID actionId) {
        return getActions().stream()
                .filter(action -> action.getName().equals(actionName) && action.getId().equals(actionId))
                .findFirst()
                .orElse(null);
    }

    public Action getPendingAction(String actionName, UUID actionId, UUID did) {
        Action action = getAction(actionName, actionId);
        if (action == null || action.terminal()) {
            throw new UnexpectedActionException(name, number, actionName, actionId, did);
        }

        return action;
    }

    public boolean resume(@NotNull List<ResumeMetadata> resumeMetadata, OffsetDateTime now) {
        Action lastAction = lastAction();
        if (lastAction == null || lastAction.getState() != ActionState.ERROR) {
            return false;
        }

        lastAction.retry(resumeMetadata.stream().filter(this::metadataFlowMatches).toList(), now);
        updateState(now);
        return true;
    }

    private boolean metadataFlowMatches(ResumeMetadata resumeMetadata) {
        return resumeMetadata.getFlow().equals(name);
    }

    public boolean hasActionInState(ActionState actionState) {
        return actions.stream().anyMatch(action -> action.getState().equals(actionState));
    }

    public boolean acknowledgeError(OffsetDateTime now, String reason) {
        boolean acked = !actions.isEmpty() && actions.getLast().acknowledgeError(now, reason);
        if (acked) {
            modified = now;
        }
        return acked;
    }

    public boolean hasFinalAction(String name) {
        return getActions().stream().anyMatch(action -> action.getName().equals(name) &&
                action.getState() != ActionState.RETRIED && action.terminal());
    }

    public void updateState(OffsetDateTime now) {
        modified = now;
        ActionState lastState = lastActionState();
        state = switch(lastState) {
            case null -> DeltaFileFlowState.COMPLETE;
            case ERROR -> DeltaFileFlowState.ERROR;
            case CANCELLED -> DeltaFileFlowState.CANCELLED;
            case COMPLETE -> hasPendingAnnotations() ? DeltaFileFlowState.PENDING_ANNOTATIONS : DeltaFileFlowState.COMPLETE;
            case JOINED, FILTERED, SPLIT -> DeltaFileFlowState.COMPLETE;
            default -> DeltaFileFlowState.IN_FLIGHT;
        };
    }

    public void removePendingAnnotations(Set<String> receivedAnnotations) {
        this.pendingAnnotations = this.pendingAnnotations != null ? new HashSet<>(pendingAnnotations) : new HashSet<>();
        this.pendingAnnotations.removeAll(receivedAnnotations);
        updateState(OffsetDateTime.now());
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
        return !actions.isEmpty() ? actions.getLast().getType() : null;
    }

    public ActionState lastActionState() {
        return !actions.isEmpty() ? actions.getLast().getState() : null;
    }

    public List<Content> lastActionContent() {
        return !actions.isEmpty() ?  actions.getLast().getContent() : List.of();
    }
}
