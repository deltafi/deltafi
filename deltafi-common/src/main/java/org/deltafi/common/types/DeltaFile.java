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
import lombok.*;
import org.deltafi.common.content.Segment;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.core.exceptions.MultipleActionException;
import org.deltafi.core.exceptions.UnexpectedActionException;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Update;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document
public class DeltaFile {
  // TODO - change the name of the collection and create a shard key, remove the old collection or migrate over?
  @Id
  private String did;
  private List<String> parentDids;
  private boolean aggregate;
  private List<String> childDids;
  private int requeueCount;
  private long ingressBytes;
  private long referencedBytes;
  private long totalBytes;
  private DeltaFileStage stage;
  private List<Action> actions;
  private SourceInfo sourceInfo;
  private Map<String, String> annotations = new HashMap<>();
  private Set<String> annotationKeys = new HashSet<>();
  private List<Egress> egress = new ArrayList<>();
  private OffsetDateTime created;
  private OffsetDateTime modified;
  private OffsetDateTime contentDeleted;
  private String contentDeletedReason;
  @Setter(AccessLevel.NONE)
  private OffsetDateTime errorAcknowledged;
  private String errorAcknowledgedReason;
  @Setter(AccessLevel.NONE)
  private Boolean testMode;
  private String testModeReason;
  private Boolean egressed;
  private Boolean filtered;
  private OffsetDateTime replayed;
  private String replayDid;
  private OffsetDateTime nextAutoResume;
  private String nextAutoResumeReason;
  private Set<String> pendingAnnotationsForFlows;

  @Builder.Default
  private boolean inFlight = true;
  @Builder.Default
  private boolean terminal = false;
  @Builder.Default
  private boolean contentDeletable = false;

  @Version
  @Getter
  @Setter
  @JsonIgnore
  private long version;

  @Getter
  @JsonIgnore
  @Setter
  private OffsetDateTime cacheTime = null;

  @Transient
  private DeltaFile snapshot = null;

  public final static int CURRENT_SCHEMA_VERSION = 8;
  private int schemaVersion;

  public DeltaFile(DeltaFile other) {
    this.did = other.did;
    this.parentDids = other.parentDids == null ? null : new ArrayList<>(other.parentDids);
    this.aggregate = other.aggregate;
    this.childDids = other.childDids == null ? null : new ArrayList<>(other.childDids);
    this.requeueCount = other.requeueCount;
    this.ingressBytes = other.ingressBytes;
    this.referencedBytes = other.referencedBytes;
    this.totalBytes = other.totalBytes;
    this.stage = other.stage;
    this.actions = other.actions == null ? null : other.actions.stream().map(Action::new).toList();
    this.sourceInfo = other.sourceInfo == null ? null : new SourceInfo(other.sourceInfo);
    this.annotations = other.annotations == null ? null : new HashMap<>(other.annotations);
    this.annotationKeys = other.annotationKeys == null ? null :new HashSet<>(other.annotationKeys);
    this.egress = other.egress == null ? null : other.egress.stream().map(Egress::new).toList();
    this.created = other.created;
    this.modified = other.modified;
    this.contentDeleted = other.contentDeleted;
    this.contentDeletedReason = other.contentDeletedReason;
    this.errorAcknowledged = other.errorAcknowledged;
    this.errorAcknowledgedReason = other.errorAcknowledgedReason;
    this.testMode = other.testMode;
    this.testModeReason = other.testModeReason;
    this.egressed = other.egressed;
    this.filtered = other.filtered;
    this.replayed = other.replayed;
    this.replayDid = other.replayDid;
    this.nextAutoResume = other.nextAutoResume;
    this.nextAutoResumeReason = other.nextAutoResumeReason;
    this.pendingAnnotationsForFlows = other.pendingAnnotationsForFlows == null ? null : new HashSet<>(other.pendingAnnotationsForFlows);
    this.inFlight = other.inFlight;
    this.terminal = other.terminal;
    this.contentDeletable = other.contentDeletable;
    this.version = other.version;
    this.cacheTime = other.cacheTime;
    this.schemaVersion = other.schemaVersion;
    this.snapshot = null;
  }

  public void snapshot() {
    this.snapshot = new DeltaFile(this);
  }

  public List<Action> getActions() {
    return actions == null ? Collections.emptyList() : actions;
  }

  public Map<String, String> getAnnotations() {
    return annotations == null ? Collections.emptyMap() : annotations;
  }

  public Set<String> getAnnotationKeys() {
    return annotationKeys == null ? Collections.emptySet() : annotationKeys;
  }

  public void setStage(DeltaFileStage stage) {
      this.stage = stage;
      updateFlags();
  }

  public void updateFlags() {
    inFlight = stage == DeltaFileStage.IN_FLIGHT;
    terminal = !inFlight && !(stage == DeltaFileStage.ERROR && errorAcknowledged == null) &&
            (pendingAnnotationsForFlows == null || pendingAnnotationsForFlows.isEmpty());
    contentDeletable = terminal && contentDeleted == null && totalBytes > 0;
  }

  public void setContentDeleted(OffsetDateTime contentDeleted) {
    this.contentDeleted = contentDeleted;
    updateFlags();
  }

  /**
   * Get the cumulative metadata from all actions that amend data through the load and enrich actions
   * as well as retried format actions
   * This method retrieves a copy of source metadata, and then applies changes from all
   * actions that amended data. For each such action, the method adds its metadata to the Map.
   * If any metadata key is present in the action's deleteMetadataKeys, the corresponding
   * entry is removed from the Map.
   *
   * @return A Map containing the resulting metadata
   */
  public Map<String, String> getMetadata() {
    if (actions == null) {
      return Collections.emptyMap();
    }
    Map<String, String> metadata = new HashMap<>();
    for (Action action : actions) {
      metadata.putAll(action.getMetadata());
      for (String key : action.getDeleteMetadataKeys()) {
        metadata.remove(key);
      }
    }

    return metadata;
  }

  public List<Action> erroredActions() {
    return getActions().stream().filter(a -> a.getState() == ActionState.ERROR).collect(Collectors.toList());
  }

  public Action queueAction(String flow, String name, ActionType type, boolean coldQueue) {
    Optional<Action> maybeAction = actionNamed(flow, name);

    if (maybeAction.isPresent()) {
      setActionState(maybeAction.get(), coldQueue ? ActionState.COLD_QUEUED : ActionState.QUEUED, null, null);
      return maybeAction.get();
    } else {
      return queueNewAction(flow, name, type, coldQueue);
    }
  }

    public Action queueNewAction(String flow, String name, ActionType type, boolean coldQueue) {
        return addAction(flow, name, type, coldQueue ? ActionState.COLD_QUEUED : ActionState.QUEUED);
    }

    public Action addAction(String flow, String name, ActionType type, ActionState state) {
        OffsetDateTime now = OffsetDateTime.now();
        Action action = Action.builder()
                .name(name)
                .type(type)
                .flow(flow)
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
            .filter(a -> a.getName().equals(name) && retried(a))
            .reduce((first, second) -> second);
    return action.map(Action::getAttempt).orElse(0);
  }

  /* Get the most recent action with the given name */
  public Optional<Action> actionNamed(String flow, String name) {
    return getActions().stream()
            .filter(action -> action.getFlow().equals(flow) && action.getName().equals(name) && !retried(action))
            .reduce((first, second) -> second);
  }

  public Optional<Action> firstActionError() {
    return getActions().stream()
            .filter(a -> a.getState().equals(ActionState.ERROR))
            .findFirst();
  }

  public boolean isNewAction(String flow, String name) {
    return actionNamed(flow, name).isEmpty();
  }

  public void completeAction(ActionEvent event) {
    completeAction(event.getFlow(), event.getAction(), event.getStart(), event.getStop(), null, null, null);
  }

  public void completeAction(ActionEvent event, List<Content> content, Map<String, String> metadata,
          List<String> deleteMetadataKeys) {
    completeAction(event.getFlow(), event.getAction(), event.getStart(), event.getStop(), content, metadata,
            deleteMetadataKeys);
  }

  public Action completeAction(String flow, String name, OffsetDateTime start, OffsetDateTime stop) {
    return completeAction(flow, name, start, stop, null, null, null);
  }

  public Action completeAction(String flow, String name, OffsetDateTime start, OffsetDateTime stop, List<Content> content,
          Map<String, String> metadata, List<String> deleteMetadataKeys) {
    Optional<Action> optionalAction = getActions().stream()
            .filter(action -> action.getFlow().equals(flow) && action.getName().equals(name) && !action.terminal())
            .findFirst();
    if (optionalAction.isEmpty()) {
      throw new UnexpectedActionException(flow, name, did, queuedActions());
    }

    Action action = optionalAction.get();
    setActionState(action, ActionState.COMPLETE, start, stop);

    if (content != null) {
      action.setContent(content);
    }

    if (metadata != null) {
      action.setMetadata(metadata);
    }

    if (deleteMetadataKeys != null) {
      action.setDeleteMetadataKeys(deleteMetadataKeys);
    }

    return action;
  }

  public Action split(ActionEvent event, List<String> childDids) {
    Optional<Action> optionalAction = getActions().stream()
            .filter(action -> action.getFlow().equals(event.getFlow()) && action.getName().equals(event.getAction()) && !action.terminal())
            .findFirst();
    if (optionalAction.isEmpty()) {
      throw new UnexpectedActionException(event.getFlow(), event.getAction(), did, queuedActions());
    }

    Action action = optionalAction.get();
    setActionState(action, ActionState.SPLIT, event.getStart(), event.getStop());

    this.childDids = childDids;
    this.stage = DeltaFileStage.COMPLETE;

    return action;
  }

  public void collectedAction(String flow, String name, OffsetDateTime start, OffsetDateTime stop) {
    getActions().stream()
            .filter(action -> action.getFlow().equals(flow) && action.getName().equals(name) && !action.terminal())
            .forEach(action -> setActionState(action, ActionState.COLLECTED, start, stop));
  }

  public void filterAction(ActionEvent event) {
    getActions().stream()
            .filter(action -> action.getFlow().equals(event.getFlow()) && action.getName().equals(event.getAction()) && !action.terminal())
            .forEach(action -> setFilteredActionState(action, event.getStart(), event.getStop(), event.getFilter().getMessage(), event.getFilter().getContext()));
  }

  public Action lastAction() {
    if (actions == null || actions.isEmpty()) {
      return null;
    }

    return actions.get(actions.size() - 1);
  }

  public void errorAction(ActionEvent event) {
    errorAction(event.getFlow(), event.getAction(), event.getStart(), event.getStop(), event.getError().getCause(),
            event.getError().getContext());
  }

  public void errorAction(ActionEvent event, String policyName, Integer delay) {
    setNextAutoResumeReason(policyName);
    errorAction(event.getFlow(), event.getAction(), event.getStart(), event.getStop(), event.getError().getCause(),
            event.getError().getContext(), event.getStop().plusSeconds(delay));
  }

  public void errorAction(ActionEvent event, String errorCause, String errorContext) {
    errorAction(event.getFlow(), event.getAction(), event.getStart(), event.getStop(), errorCause, errorContext, null);
  }

  public void errorAction(String flow, String name, OffsetDateTime start, OffsetDateTime stop, String errorCause, String errorContext) {
    errorAction(flow, name, start, stop, errorCause, errorContext, null);
  }

  public void errorAction(String flow, String name, OffsetDateTime start, OffsetDateTime stop, String errorCause, String errorContext, OffsetDateTime nextAutoResume) {
    getActions().stream()
            .filter(action -> action.getFlow().equals(flow) && action.getName().equals(name) && !action.terminal())
            .forEach(action -> setActionState(action, ActionState.ERROR, start, stop, errorCause, errorContext, nextAutoResume));
  }

  public List<String> retryErrors(@NotNull List<ResumeMetadata> resumeMetadata) {
    List<Action> actionsToRetry = getActions().stream()
            .filter(action -> action.getState().equals(ActionState.ERROR))
            .toList();

    for (Action action : actionsToRetry) {
      action.setState(ActionState.RETRIED);
      resumeMetadata.stream()
              .filter(r -> r.getFlow().equals(action.getFlow()) && r.getAction().equals(action.getName()))
              .forEach(r -> {
                if (r.getMetadata() != null) {
                  action.setMetadata(KeyValueConverter.convertKeyValues(r.getMetadata()));
                }
                if (r.getDeleteMetadataKeys() != null) {
                  action.setDeleteMetadataKeys(r.getDeleteMetadataKeys());
                }
              });
    }

    setNextAutoResume(null);

    return actionsToRetry.stream().map(Action::getName).toList();
  }

  private void setActionState(Action action, ActionState actionState, OffsetDateTime start, OffsetDateTime stop) {
    setActionState(action, actionState, start, stop, null, null, null);
  }

  private void setFilteredActionState(Action action, OffsetDateTime start, OffsetDateTime stop, String filteredCause, String filteredContext) {
    action.setFilteredCause(filteredCause);
    action.setFilteredContext(filteredContext);
    setActionState(action, ActionState.FILTERED, start, stop);
  }

  private void setActionState(Action action, ActionState actionState, OffsetDateTime start, OffsetDateTime stop, String errorCause, String errorContext, OffsetDateTime nextAutoResume) {
    OffsetDateTime now = OffsetDateTime.now();
    action.setState(actionState);
    if (action.getCreated() == null) {
      action.setCreated(now);
    }
    action.setStart(start);
    action.setStop(stop);
    action.setModified(now);
    action.setErrorCause(errorCause);
    action.setErrorContext(errorContext);
    setModified(now);
    setNextAutoResume(nextAutoResume);
  }

  public void setTestModeReason(String reason) {
    testMode = null != reason;
    testModeReason = reason;
  }

  public List<String> queuedActions() {
    return getActions().stream().filter(Action::queued).map(Action::getName).toList();
  }

  public void addAnnotations(Map<String, String> metadata) {
    if (null == metadata) {
      return;
    }

    if (annotations == null) {
      annotations = new HashMap<>();
    }
    this.annotations.putAll(metadata);
    if (annotationKeys == null) {
      annotationKeys = new HashSet<>();
    }
    this.annotationKeys.addAll(metadata.keySet());
  }

  public void addAnnotationsIfAbsent(Map<String, String> metadata) {
    metadata.forEach(this::addAnnotationIfAbsent);
  }

  public void addAnnotationIfAbsent(String key, String value) {
    if (null == key || (annotations != null && annotations.containsKey(key))) {
      return;
    }

    if (annotations == null) {
      annotations = new HashMap<>();
    }
    this.annotations.put(key, value);
    if (annotationKeys == null) {
      annotationKeys = new HashSet<>();
    }
    this.annotationKeys.add(key);
  }

  public void addEgressFlow(@NotNull String flow) {
    if (!getEgress().stream().map(Egress::getFlow).toList().contains(flow)) {
      getEgress().add(new Egress(flow));
    }
  }

  public boolean hasErroredAction() {
    return hasActionInState(ActionState.ERROR);
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public boolean hasPendingActions() {
    return getActions().stream().anyMatch(action -> !action.terminal());
  }

  public boolean hasFilteredAction() {
    return hasActionInState(ActionState.FILTERED);
  }

  public boolean hasCollectingAction() {
    return hasActionInState(ActionState.COLLECTING);
  }

  public boolean hasActionInState(ActionState actionState) {
    return getActions().stream().anyMatch(action -> action.getState().equals(actionState));
  }

  public void ensurePendingAction(String flow, String name) {
    long actionCount = countPendingActions(flow, name);
    if (actionCount == 0) {
      throw new UnexpectedActionException(flow, name, did, queuedActions());
    } else if (actionCount > 1) {
      throw new MultipleActionException(flow, name, did);
    }
  }

  private long countPendingActions(String flow, String name) {
    return getActions().stream().filter(action -> action.getFlow().equals(flow) && action.getName().equals(name) && !action.terminal()).count();
  }

  private boolean retried(Action action) {
    return action.getState().equals(ActionState.RETRIED);
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public boolean hasTerminalAction(String flow, String name) {
    return getActions().stream().anyMatch(action -> action.getFlow().equals(flow) && action.getName().equals(name) &&
            !retried(action) && action.terminal());
  }

  public boolean hasCompletedAction(String flow, String name) {
    return hasActionInState(flow, name, ActionState.COMPLETE);
  }

  public boolean hasCompletedActions(String flow, List<String> names) {
    return names.stream().allMatch(name -> hasCompletedAction(flow, name));
  }

  public boolean hasCollectedAction(String flow, String name) {
    return hasActionInState(flow, name, ActionState.COLLECTED);
  }

  public boolean hasActionInState(String flow, String name, ActionState state) {
    return getActions().stream().anyMatch(action -> action.getFlow().equals(flow) && action.getName().equals(name) &&
            action.getState().equals(state));
  }

  public String sourceMetadata(String key) {
    return getSourceInfo().getMetadata().get(key);
  }

  public String sourceMetadata(String key, String defaultValue) {
    return getSourceInfo().getMetadata().getOrDefault(key, defaultValue);
  }

  /**
   * Add the given flow to the set of pendingAnnotationsForFlows for this DeltaFile
   * Do nothing if the given set is null or empty
   * @param flowName name of the flow that requires annotations for this DeltaFile
   */
  public void addPendingAnnotationsForFlow(String flowName) {
    if (flowName == null || flowName.isBlank()) {
      return;
    }

    if (this.pendingAnnotationsForFlows == null) {
      this.pendingAnnotationsForFlows = new HashSet<>();
    }

    this.pendingAnnotationsForFlows.add(flowName);
    updateFlags();
  }

  /**
   * Check if the set of expected annotations are satisfied for the given flow.
   * If the all the annotations are present, remove the flow from the pendingAnnotationsForFlows
   * set. If pendingAnnotationsForFlows is empty after removing the flow, set it to null.
   * @param flow name of the flow that could be removed from pendingAnnotationsForFlows
   * @param expectedAnnotations set of annotations expected for the given flow
   */
  public void updatePendingAnnotationsForFlows(String flow, Set<String> expectedAnnotations) {
    if (this.pendingAnnotationsForFlows == null || !this.pendingAnnotationsForFlows.contains(flow)) {
      return;
    }

    // if there are no expected annotations for this flow, remove it from the pending list
    if (pendingAnnotations(expectedAnnotations).isEmpty()) {
      pendingAnnotationsForFlows.remove(flow);
      if (pendingAnnotationsForFlows.isEmpty()) {
        pendingAnnotationsForFlows = null;
      }
    }

    updateFlags();
  }

  /**
   * Get the set of annotations that are still pending from the given set of expected annotations
   * @param expectedAnnotations annotations that are expected to be set on the DeltaFile
   * @return annotations that have not been added to the DeltaFile yet
   */
  public Set<String> pendingAnnotations(Set<String> expectedAnnotations) {
    // make sure the expectedAnnotations set is modifiable
    expectedAnnotations = expectedAnnotations != null ? new HashSet<>(expectedAnnotations) : new HashSet<>();

    Set<String> indexedKeys = getAnnotationKeys();
    indexedKeys.forEach(expectedAnnotations::remove);

    return expectedAnnotations;
  }

  public Action lastCompleteAction() {
    return getActions().stream()
            .filter(Action::completeOrRetried)
            .reduce((first, second) -> second)
            .orElse(null);
  }

  public @NotNull List<Content> lastContent() {
    Action lastAction = lastCompleteAction();
    if (lastAction == null || lastAction.getContent() == null) {
      return Collections.emptyList();
    }

    return lastAction.getContent();
  }

  public @NotNull List<Egress> getEgress() {
    if (egress == null) egress = new ArrayList<>();
    return egress;
  }

  public DeltaFileMessage forQueue() {
    return DeltaFileMessage.builder()
            .contentList(lastContent())
            .metadata(getMetadata())
            .build();
  }

  public Set<Segment> referencedSegments() {
    if (actions == null) {
      return Collections.emptySet();
    }
    return actions.stream()
            .flatMap(p -> p.getContent().stream())
            .flatMap(c -> c.getSegments().stream())
            .collect(Collectors.toSet());
  }

  public Set<Segment> storedSegments() {
    if (actions == null) {
      return Collections.emptySet();
    }
    return actions.stream()
            .flatMap(p -> p.getContent().stream())
            .flatMap(c -> c.getSegments().stream())
            .filter(s -> s.getDid().equals(getDid()))
            .collect(Collectors.toSet());
  }

  public void recalculateBytes() {
    setReferencedBytes(Segment.calculateTotalSize(referencedSegments()));
    setTotalBytes(Segment.calculateTotalSize(storedSegments()));
    updateFlags();
  }

  public void cancel() {
    cancelQueuedActions();
    setStage(DeltaFileStage.CANCELLED);
    setNextAutoResume(null);
    setNextAutoResumeReason(null);
  }

  public void cancelQueuedActions() {
    OffsetDateTime now = OffsetDateTime.now();
    getActions().stream()
            .filter(Action::queued)
            .forEach(a -> {
              a.setState(ActionState.CANCELLED);
              a.setModified(now);
            });
    setModified(now);
  }

  public void incrementRequeueCount() {
    ++requeueCount;
  }

  public boolean canBeCancelled() {
    return !inactiveStage() || nextAutoResume != null;
  }
  public boolean inactiveStage() {
    return getStage() == DeltaFileStage.COMPLETE || getStage() == DeltaFileStage.ERROR || getStage() == DeltaFileStage.CANCELLED;
  }

  public Action lastAction(String actionName) {
    if (actionName == null) {
      return null;
    }

    return actions.stream()
            .filter(a -> a.getName().equals(actionName))
            .reduce((first, second) -> second)
            .orElse(null);
  }

  public void acknowledgeError(@NotNull OffsetDateTime now, String reason) {
    errorAcknowledged = now;
    errorAcknowledgedReason = reason;
    setNextAutoResume(null);
    setNextAutoResumeReason(null);
    updateFlags();
  }

  public void clearErrorAcknowledged() {
    errorAcknowledged = null;
    errorAcknowledgedReason = null;
    updateFlags();
  }

  public Update generateUpdate() {
    // only update fields that are modified after creation in Java code
    Update update = new Update();
    boolean updated = false;
    if (!Objects.equals(this.childDids, snapshot.childDids)) {
      update.set("childDids", this.childDids);
      updated = true;
    }
    if (!Objects.equals(this.referencedBytes, snapshot.referencedBytes)) {
      update.set("referencedBytes", this.referencedBytes);
      updated = true;
    }
    if (!Objects.equals(this.totalBytes, snapshot.totalBytes)) {
      update.set("totalBytes", this.totalBytes);
      updated = true;
    }
    if (!Objects.equals(this.stage, snapshot.stage)) {
      update.set("stage", this.stage);
      updated = true;
    }
    if (!Objects.equals(this.actions, snapshot.actions)) {
      if (actions.size() == snapshot.actions.size()) {
        for (int i = 0; i < snapshot.actions.size(); i++) {
          if (!Objects.equals(this.actions.get(i), snapshot.actions.get(i))) {
            update.set(String.format("actions.%d", i), this.actions.get(i));
          }
        }
      } else {
        // mongo does not support both sets and pushes in the same update, so we have to send the whole object
        update.set("actions", this.actions);
      }
      updated = true;
    }
    if (!Objects.equals(this.annotations, snapshot.annotations)) {
      update.set("annotations", this.annotations);
      updated = true;
    }
    if (!Objects.equals(this.annotationKeys, snapshot.annotationKeys)) {
      update.set("annotationKeys", this.annotationKeys);
      updated = true;
    }
    if (!Objects.equals(this.egress, snapshot.egress)) {
      update.set("egress", this.egress);
      updated = true;
    }
    if (!Objects.equals(this.modified, snapshot.modified)) {
      update.set("modified", this.modified);
      updated = true;
    }
    if (!Objects.equals(this.errorAcknowledged, snapshot.errorAcknowledged)) {
      update.set("errorAcknowledged", this.errorAcknowledged);
      updated = true;
    }
    if (!Objects.equals(this.errorAcknowledgedReason, snapshot.errorAcknowledgedReason)) {
      update.set("errorAcknowledgedReason", this.errorAcknowledgedReason);
      updated = true;
    }
    if (!Objects.equals(this.testMode, snapshot.testMode)) {
      update.set("testMode", this.testMode);
      updated = true;
    }
    if (!Objects.equals(this.testModeReason, snapshot.testModeReason)) {
      update.set("testModeReason", this.testModeReason);
      updated = true;
    }
    if (!Objects.equals(this.egressed, snapshot.egressed)) {
      update.set("egressed", this.egressed);
      updated = true;
    }
    if (!Objects.equals(this.filtered, snapshot.filtered)) {
      update.set("filtered", this.filtered);
      updated = true;
    }
    if (!Objects.equals(this.nextAutoResume, snapshot.nextAutoResume)) {
      update.set("nextAutoResume", this.nextAutoResume);
      updated = true;
    }
    if (!Objects.equals(this.nextAutoResumeReason, snapshot.nextAutoResumeReason)) {
      update.set("nextAutoResumeReason", this.nextAutoResumeReason);
      updated = true;
    }
    if (!Objects.equals(this.pendingAnnotationsForFlows, snapshot.pendingAnnotationsForFlows)) {
      update.set("pendingAnnotationsForFlows", this.pendingAnnotationsForFlows);
      updated = true;
    }
    if (!Objects.equals(this.schemaVersion, snapshot.schemaVersion)) {
      update.set("schemaVersion", this.schemaVersion);
      updated = true;
    }
    if (!Objects.equals(this.inFlight, snapshot.inFlight)) {
      update.set("inFlight", this.inFlight);
      updated = true;
    }
    if (!Objects.equals(this.terminal, snapshot.terminal)) {
      update.set("terminal", this.terminal);
      updated = true;
    }
    if (!Objects.equals(this.contentDeletable, snapshot.contentDeletable)) {
      update.set("contentDeletable", this.contentDeletable);
      updated = true;
    }

    if (!updated) {
      return null;
    }

    update.set("version", this.version + 1);

    return update;
  }

  @JsonIgnore
  public Map<String, String> getImmutableMetadata() {
    return Collections.unmodifiableMap(getMetadata());
  }

  @JsonIgnore
  public List<Content> getImmutableContent() {
    return lastContent().stream().map(Content::copy).toList();
  }
}
