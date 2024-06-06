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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.*;
import org.deltafi.core.exceptions.UnexpectedFlowException;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Sharded;
import org.springframework.data.mongodb.core.query.Update;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document("deltaFiles")
@Sharded
public class DeltaFile {
  @Id
  @Builder.Default
  private UUID did = UUID.randomUUID();
  private String name;
  private String normalizedName;
  private String dataSource;
  @Builder.Default
  private List<UUID> parentDids = new ArrayList<>();
  private UUID collectId;
  @Builder.Default
  private List<UUID> childDids = new ArrayList<>();
  @Builder.Default
  private List<DeltaFileFlow> flows = new ArrayList<>();
  private int requeueCount;
  private long ingressBytes;
  private long referencedBytes;
  private long totalBytes;
  private DeltaFileStage stage;
  @Builder.Default
  private Map<String, String> annotations = new HashMap<>();
  @Builder.Default
  private Set<String> annotationKeys = new HashSet<>();
  @Builder.Default
  private List<String> egressFlows = new ArrayList<>();
  private OffsetDateTime created;
  private OffsetDateTime modified;
  private OffsetDateTime contentDeleted;
  private String contentDeletedReason;
  private Boolean egressed;
  private Boolean filtered;
  private OffsetDateTime replayed;
  private UUID replayDid;

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
  @Builder.Default
  private OffsetDateTime cacheTime = null;

  @Transient
  @Builder.Default
  private DeltaFile snapshot = null;

  public static final int CURRENT_SCHEMA_VERSION = 1;
  private int schemaVersion;

  public DeltaFile(DeltaFile other) {
    this.did = other.did;
    this.parentDids = other.parentDids == null ? null : new ArrayList<>(other.parentDids);
    this.collectId = other.collectId;
    this.childDids = other.childDids == null ? null : new ArrayList<>(other.childDids);
    this.requeueCount = other.requeueCount;
    this.ingressBytes = other.ingressBytes;
    this.referencedBytes = other.referencedBytes;
    this.totalBytes = other.totalBytes;
    this.stage = other.stage;
    this.flows = other.flows == null ? null : other.flows.stream().map(DeltaFileFlow::new).toList();
    this.annotations = other.annotations == null ? null : new HashMap<>(other.annotations);
    this.annotationKeys = other.annotationKeys == null ? null :new HashSet<>(other.annotationKeys);
    this.egressFlows = other.egressFlows == null ? null : new ArrayList<>(other.egressFlows);
    this.created = other.created;
    this.modified = other.modified;
    this.contentDeleted = other.contentDeleted;
    this.contentDeletedReason = other.contentDeletedReason;
    this.egressed = other.egressed;
    this.filtered = other.filtered;
    this.replayed = other.replayed;
    this.replayDid = other.replayDid;
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
    terminal = !inFlight && unackErrorFlows().isEmpty() && pendingAnnotationFlows().isEmpty();
    contentDeletable = terminal && contentDeleted == null && totalBytes > 0;
  }

  public void setContentDeleted(OffsetDateTime contentDeleted) {
    this.contentDeleted = contentDeleted;
    updateFlags();
  }

  public List<DeltaFileFlow> unackErrorFlows() {
    return flows.stream().filter(DeltaFileFlow::hasUnacknowledgedError).toList();
  }

  public List<DeltaFileFlow> pendingAnnotationFlows() {
    return flows.stream().filter(DeltaFileFlow::hasPendingAnnotations).toList();
  }

  /**
   * Find all flows that have pending annotations. For each of those flows update
   * the pending annotations based on the latest set of annotation keys and update
   * the flags after the update.
   */
  public void updatePendingAnnotations() {
    List<DeltaFileFlow> pendingAnnotations = pendingAnnotationFlows();

    if (!pendingAnnotations.isEmpty()) {
      pendingAnnotations.forEach(deltaFileFlow -> deltaFileFlow.removePendingAnnotations(this.annotationKeys));
      updateFlags();
    }
  }

  public void setPendingAnnotations(String flowName, Set<String> expectedAnnotations, OffsetDateTime now) {
    Set<String> pendingAnnotations = getPendingAnnotations(expectedAnnotations);

    flows.stream().filter(flow -> flow.getType() == FlowType.EGRESS && flow.getName().equals(flowName))
            .forEach(deltaFileFlow -> setPendingAnnotations(deltaFileFlow, pendingAnnotations, now));

    updateFlags();
  }

  public Set<String> getPendingAnnotations(Set<String> expectedAnnotations) {
    Set<String> pendingAnnotations = expectedAnnotations != null ? new HashSet<>(expectedAnnotations) : new HashSet<>();

    if (annotationKeys != null) {
      pendingAnnotations.removeAll(annotationKeys);
    }

    return pendingAnnotations;
  }

  private void setPendingAnnotations(DeltaFileFlow flow, Set<String> expectedAnnotations, OffsetDateTime now) {
    flow.setPendingAnnotations(expectedAnnotations);
    flow.updateState(now);
  }

  public List<DeltaFileFlow> erroredFlows() {
    return flows.stream()
            .filter(f -> f.getState() == DeltaFileFlowState.ERROR)
            .toList();
  }

  /**
   * Find all flows that were collecting on the given CollectId and update the
   * collecting actions to a state of collected. Update the state of each modified
   * flow and then update this DeltaFiles state.
   * @param collectId did of the child DeltaFile that collected the data
   * @param actionName name of the action to mark as Collected
   * @param start start time of the action
   * @param stop stop time of the action
   * @param now current time
   */
  public void collectedAction(UUID collectId, String actionName, OffsetDateTime start, OffsetDateTime stop, OffsetDateTime now) {
    collectingFlows(collectId).forEach(f -> collectAction(f, actionName, start, stop, now));
    updateState(now);
  }

  private void collectAction(DeltaFileFlow deltaFileFlow, String actionName, OffsetDateTime start, OffsetDateTime stop, OffsetDateTime now) {
    deltaFileFlow.getActions().stream()
            .filter(a -> a.getName().equals(actionName) && a.getState() == ActionState.COLLECTING)
            .forEach(action -> action.changeState(ActionState.COLLECTED, start, stop, now));
    deltaFileFlow.updateState(now);
  }

  public void timeoutCollectAction(UUID collectId, String actionName, OffsetDateTime now, String reason) {
    collectingFlows(collectId).forEach(f -> timeoutCollectAction(f, actionName, now, reason));
    updateState(now);
  }

  private void timeoutCollectAction(DeltaFileFlow deltaFileFlow, String actionName, OffsetDateTime now, String reason) {
    deltaFileFlow.getActions().stream()
            .filter(a -> a.getName().equals(actionName) && a.getState() == ActionState.COLLECTING)
            .forEach(a -> a.error(now, now, now, "Failed collect", reason));
    deltaFileFlow.setState(DeltaFileFlowState.ERROR);
  }

  private Stream<DeltaFileFlow> collectingFlows(UUID collectId) {
    return flows.stream().filter(flow -> collectedWithId(flow, collectId));
  }

  private boolean collectedWithId(DeltaFileFlow flow, UUID collectId) {
    return collectId.equals(flow.getCollectId()) && !flow.terminal();
  }

  public List<DeltaFileFlow> resumeErrors(@NotNull List<ResumeMetadata> resumeMetadata, OffsetDateTime now) {
    List<DeltaFileFlow> retries = flows.stream()
            .filter(f -> f.resume(resumeMetadata, now))
            .toList();

    if (!retries.isEmpty()) {
      modified = now;
    }

    return retries;
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

  public boolean hasPendingActions() {
    return flows.stream().anyMatch(flow -> !flow.terminal());
  }

  public boolean hasErrors() {
    return flows.stream().anyMatch(flow -> flow.getState() == DeltaFileFlowState.ERROR);
  }

  public boolean hasCollectingAction() {
    return flows.stream().anyMatch(f -> f.hasActionInState(ActionState.COLLECTING));
  }

  public DeltaFileFlow getFlow(String flowName, int flowId) {
    return flows.stream().filter(f -> f.getId() == flowId && f.getName().equals(flowName)).findFirst().orElse(null);
  }

  public DeltaFileFlow getPendingFlow(String flowName, int flowId) {
    DeltaFileFlow flow = getFlow(flowName, flowId);
    if (flow == null || flow.terminal()) {
      throw new UnexpectedFlowException(flowName, flowId, did);
    }

    return flow;
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

  public Set<Segment> referencedSegments() {
    return flows.stream()
            .flatMap(f -> f.uniqueSegments().stream())
            .collect(Collectors.toSet());
  }

  public Set<Segment> storedSegments() {
    return flows.stream()
            .flatMap(f -> f.uniqueSegments().stream())
            .filter(s -> s.getDid().equals(getDid()))
            .collect(Collectors.toSet());
  }

  public void recalculateBytes() {
    setReferencedBytes(Segment.calculateTotalSize(referencedSegments()));
    setTotalBytes(Segment.calculateTotalSize(storedSegments()));
    updateFlags();
  }

  public void cancel(OffsetDateTime now) {
    if (!canBeCancelled()) {
      return;
    }
    setStage(DeltaFileStage.CANCELLED);
    flows.forEach(d -> d.cancel(now));
    modified = now;
  }

  public void incrementRequeueCount() {
    ++requeueCount;
  }

  public boolean canBeCancelled() {
    return !inactiveStage() || flows.stream().anyMatch(DeltaFileFlow::hasAutoResume);
  }
  public boolean inactiveStage() {
    return getStage() == DeltaFileStage.COMPLETE || getStage() == DeltaFileStage.ERROR || getStage() == DeltaFileStage.CANCELLED;
  }

  public void acknowledgeErrors(OffsetDateTime now, String reason) {
    boolean found = flows.stream()
            .map(f -> f.acknowledgeError(now, reason))
            .reduce(false, (a, b) -> a || b);
    if (found) {
      modified = now;
      updateFlags();
    }
  }

  public void clearErrorAcknowledged(OffsetDateTime now) {
    boolean found = flows.stream()
            .map(f -> f.clearErrorAcknowledged(now))
            .reduce(false, (a, b) -> a || b);
    if (found) {
      modified = now;
      updateFlags();
    }
  }

  public void updateState(OffsetDateTime now) {
    modified = now;
    if (!hasPendingActions()) {
      stage = hasErrors() ? DeltaFileStage.ERROR : DeltaFileStage.COMPLETE;
    } else if (stage != DeltaFileStage.CANCELLED) {
      stage = DeltaFileStage.IN_FLIGHT;
    }
    recalculateBytes();
    updateFlags();
  }

  public DeltaFileFlow addFlow(String name, FlowType type, DeltaFileFlow previousFlow, OffsetDateTime now) {
    return addFlow(name, type, previousFlow, Set.of(), now);
  }

  public DeltaFileFlow addFlow(String name, FlowType type, DeltaFileFlow previousFlow, Set<String> subscribedTopics, OffsetDateTime now) {
    DeltaFileFlow flow = DeltaFileFlow.builder()
            .name(name)
            .id(flows.stream().mapToInt(DeltaFileFlow::getId).max().orElse(0) + 1)
            .type(type)
            .state(DeltaFileFlowState.IN_FLIGHT)
            .created(now)
            .modified(now)
            // TODO: fix this
            .flowPlan(FlowPlanCoordinates.builder()
                    .build())
            .input(DeltaFileFlowInput.builder()
                    .metadata(previousFlow.getMetadata())
                    .content(previousFlow.lastContent())
                    .topics(subscribedTopics)
                    .ancestorIds(new ArrayList<>(previousFlow.getInput().getAncestorIds()))
                    .build())
            .depth(previousFlow.getDepth() + 1)
            .testMode(previousFlow.isTestMode())
            .testModeReason(previousFlow.getTestModeReason())
            .build();
    flows.add(flow);

    if (type == FlowType.EGRESS) {
      egressFlows.add(name);
    }

    return flow;
  }

  public void removeFlowsNotDescendedFrom(int flowId) {
    flows.removeIf(f -> !f.getInput().getAncestorIds().contains(flowId));
    List<Integer> remainingFlowIds = flows.stream().map(DeltaFileFlow::getId).toList();
    flows.forEach(f -> f.getInput().getAncestorIds().retainAll(remainingFlowIds));
  }

  public void setName(String name) {
    this.name = name;
    this.normalizedName = name != null ? name.toLowerCase() : null;
  }

  public Update generateUpdate() {
    // only update fields that are modified after creation in Java code
    Update update = new Update();
    boolean updated = false;
    // did, name, normalizedName, dataSource, parentDids, and created do not change
    if (!Objects.equals(this.collectId, snapshot.collectId)) {
      update.set("collectId", this.collectId);
      updated = true;
    }
    if (!Objects.equals(this.childDids, snapshot.childDids)) {
      update.set("childDids", this.childDids);
      updated = true;
    }
    if (!Objects.equals(this.flows, snapshot.flows)) {
      if (flows.size() == snapshot.flows.size()) {
        for (int i = 0; i < snapshot.flows.size(); i++) {
          if (!Objects.equals(this.flows.get(i), snapshot.flows.get(i))) {
            update.set(String.format("flows.%d", i), this.flows.get(i));
          }
        }
      } else {
        // mongo does not support both sets and pushes in the same update, so we have to send the whole object
        update.set("flows", this.flows);
      }
      updated = true;
    }
    if (!Objects.equals(this.requeueCount, snapshot.requeueCount)) {
      update.set("requeueCount", this.requeueCount);
      updated = true;
    }
    if (!Objects.equals(this.ingressBytes, snapshot.ingressBytes)) {
      update.set("ingressBytes", this.ingressBytes);
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
    if (!Objects.equals(this.annotations, snapshot.annotations)) {
      update.set("annotations", this.annotations);
      updated = true;
    }
    if (!Objects.equals(this.annotationKeys, snapshot.annotationKeys)) {
      update.set("annotationKeys", this.annotationKeys);
      updated = true;
    }
    if (!Objects.equals(this.egressFlows, snapshot.egressFlows)) {
      update.set("egressFlows", this.egressFlows);
      updated = true;
    }
    if (!Objects.equals(this.modified, snapshot.modified)) {
      update.set("modified", this.modified);
      updated = true;
    }
    if (!Objects.equals(this.contentDeleted, snapshot.contentDeleted)) {
      update.set("contentDeleted", this.contentDeleted);
      updated = true;
    }
    if (!Objects.equals(this.contentDeletedReason, snapshot.contentDeletedReason)) {
      update.set("contentDeletedReason", this.contentDeletedReason);
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
    if (!Objects.equals(this.replayed, snapshot.replayed)) {
      update.set("replayed", this.replayed);
      updated = true;
    }
    if (!Objects.equals(this.replayDid, snapshot.replayDid)) {
      update.set("replayDid", this.replayDid);
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
    if (!Objects.equals(this.schemaVersion, snapshot.schemaVersion)) {
      update.set("schemaVersion", this.schemaVersion);
      updated = true;
    }

    if (!updated) {
      return null;
    }

    update.set("version", this.version + 1);

    return update;
  }

  /**
   * Create the ActionInput that should be sent to an Action
   * @param flow the flow on which the Action is specified
   * @param actionConfiguration Configured action
   * @param systemName system name to set in context
   * @param returnAddress the unique address of this core instance
   * @param action the action
   * @param memo memo to set in the context
   * @return ActionInput containing the ActionConfiguration
   */
  public WrappedActionInput buildActionInput(ActionConfiguration actionConfiguration, DeltaFileFlow flow, Action action, String systemName,
                                             String returnAddress, String memo) {
    WrappedActionInput actionInput = buildActionInput(actionConfiguration, flow, List.of(), action, systemName, returnAddress, memo);
    actionInput.setDeltaFileMessages(List.of(new DeltaFileMessage(flow.getMetadata(), flow.lastContent())));
    return actionInput;
  }

  /**
   * Create the ActionInput that should be sent to an Action
   * @param flow the flow on which the Action is specified
   * @param actionConfiguration Configured action
   * @param systemName system name to set in context
   * @param returnAddress the unique address of this core instance
   * @param action the action
   * @param memo memo to set in the context
   * @return ActionInput containing the ActionConfiguration
   */
  public WrappedActionInput buildActionInput(ActionConfiguration actionConfiguration, DeltaFileFlow flow, List<UUID> collectedDids, Action action, String systemName,
                                             String returnAddress, String memo) {
    return WrappedActionInput.builder()
            .queueName(actionConfiguration.getType())
            .actionContext(ActionContext.builder()
                    .flowName(flow.getName())
                    .dataSource(dataSource)
                    .flowId(flow.getId())
                    .actionName(action.getName())
                    .actionId(action.getId())
                    .did(did)
                    .deltaFileName(name)
                    .collectedDids(Objects.requireNonNullElseGet(collectedDids, List::of))
                    .collect(actionConfiguration.getCollect())
                    .systemName(systemName)
                    .memo(memo)
                    .build())
            .deltaFile(this)
            .actionParams(actionConfiguration.getInternalParameters())
            .returnAddress(returnAddress)
            .actionCreated(action.getCreated())
            .coldQueued(action.getState() == ActionState.COLD_QUEUED)
            .build();
  }
}
