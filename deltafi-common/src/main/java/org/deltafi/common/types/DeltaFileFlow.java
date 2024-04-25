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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.content.Segment;
import org.deltafi.core.exceptions.UnexpectedActionException;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Predicate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeltaFileFlow {
    private String name;
    private int id;
    private FlowType type;
    @Builder.Default
    private DeltaFileFlowState state = DeltaFileFlowState.IN_FLIGHT;
    private OffsetDateTime created;
    private OffsetDateTime modified;
    private FlowPlanCoordinates flowPlan;
    @Builder.Default
    private DeltaFileFlowInput input = new DeltaFileFlowInput();
    @Builder.Default
    private List<Action> actions = new ArrayList<>();
    @Builder.Default
    private List<String> publishTopics = new ArrayList<>();
    private int depth;
    @Builder.Default
    private Set<String> pendingAnnotations = new HashSet<>();
    boolean testMode;
    String testModeReason;

    public DeltaFileFlow(DeltaFileFlow other) {
        this.name = other.name;
        this.id = other.id;
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
    }

    /**
     * Get the cumulative metadata from all actions in the flow
     *
     * @return A Map containing the resulting metadata
     */
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

    @JsonIgnore
    public Map<String, String> getImmutableMetadata() {
        return Collections.unmodifiableMap(getMetadata());
    }

    public List<Content> lastContent() {
        return latestMatchingAction(action -> action.getState() == ActionState.COMPLETE)
                .map(Action::getContent)
                .orElseGet(this::inputContent);
    }

    private List<Content> inputContent() {
        return input != null ? input.content : List.of();
    }

    @JsonIgnore
    public List<Content> getImmutableContent() {
        return lastContent().stream().map(Content::copy).toList();
    }

    public Action lastAction() {
        return actions.isEmpty() ? null : actions.getLast();
    }

    public boolean hasUnacknowledgedError() {
        Action lastAction = lastAction();
        return lastAction.getState() == ActionState.ERROR && lastAction.getErrorAcknowledged() == null;
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
                .id(actions.size())
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
        return state == DeltaFileFlowState.COMPLETE || state == DeltaFileFlowState.CANCELLED || state == DeltaFileFlowState.ERROR;
    }

    public Action getAction(String actionName, int actionId) {
        return getActions().stream()
                .filter(action -> action.getName().equals(actionName) && action.getId() == actionId)
                .findFirst()
                .orElse(null);
    }

    public Action getPendingAction(String actionName, int actionId, String did) {
        Action action = getAction(actionName, actionId);
        if (action == null || action.terminal()) {
            throw new UnexpectedActionException(name, id, actionName, actionId, did);
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

    private boolean isActionErrored(Action action) {
        return action != null && action.getState() == ActionState.ERROR;
    }

    public boolean hasActionInState(ActionState actionState) {
        return actions.stream().anyMatch(action -> action.getState().equals(actionState));
    }

    public boolean acknowledgeError(OffsetDateTime now, String reason) {
        boolean acked = lastAction().acknowledgeError(now, reason);
        if (acked) {
            modified = now;
        }
        return acked;
    }

    public boolean clearErrorAcknowledged(OffsetDateTime now) {
        boolean cleared = lastAction().clearErrorAcknowledged(now);
        if (cleared) {
            modified = now;
        }
        return cleared;
    }

    public boolean hasCollectedAction(String name) {
        return actions.stream().anyMatch(a -> a.getName().equals(name) && a.getState() == ActionState.COLLECTED);
    }

    public boolean hasFinalAction(String name) {
        return getActions().stream().anyMatch(action -> action.getName().equals(name) &&
                action.getState() != ActionState.RETRIED && action.terminal());
    }

    public void updateState(OffsetDateTime now) {
        modified = now;
        state = switch(lastAction().getState()) {
            case ERROR -> DeltaFileFlowState.ERROR;
            case CANCELLED -> DeltaFileFlowState.CANCELLED;
            case COMPLETE -> hasPendingAnnotations() ? DeltaFileFlowState.PENDING_ANNOTATIONS : DeltaFileFlowState.COMPLETE;
            case COLLECTED, FILTERED, SPLIT -> DeltaFileFlowState.COMPLETE;
            default -> DeltaFileFlowState.IN_FLIGHT;
        };
    }

    public void removePendingAnnotations(Set<String> receivedAnnotations) {
        this.pendingAnnotations = this.pendingAnnotations != null ? new HashSet<>(pendingAnnotations) : new HashSet<>();
        this.pendingAnnotations.removeAll(receivedAnnotations);
        updateState(OffsetDateTime.now());
    }
}
