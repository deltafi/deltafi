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

import com.fasterxml.jackson.annotation.JsonManagedReference;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.*;
import org.deltafi.core.exceptions.UnexpectedFlowException;
import org.hibernate.annotations.Type;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.annotation.Version;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "delta_files")
public class DeltaFile {
  @Id
  @Builder.Default
  private UUID did = UUID.randomUUID();
  private String name;
  private String normalizedName;
  private String dataSource;
  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @Builder.Default
  private List<UUID> parentDids = new ArrayList<>();
  private UUID collectId;
  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @Builder.Default
  private List<UUID> childDids = new ArrayList<>();
  @Builder.Default
  @JoinColumn(name = "delta_file_id")
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JsonManagedReference
  @OrderBy("number ASC")
  private List<DeltaFileFlow> flows = new ArrayList<>();
  private int requeueCount;
  private long ingressBytes;
  private long referencedBytes;
  private long totalBytes;
  @Enumerated(EnumType.STRING)
  private DeltaFileStage stage;
  @Builder.Default
  @JoinColumn(name = "delta_file_id")
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JsonManagedReference
  private List<Annotation> annotations = new ArrayList<>();
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
  private long version;

  @Builder.Default
  @Transient
  private OffsetDateTime cacheTime = null;

  public static final int CURRENT_SCHEMA_VERSION = 1;
  private int schemaVersion;

  public DeltaFile(DeltaFile other) {
    this.did = other.did;
    this.name = other.name;
    this.normalizedName = other.normalizedName;
    this.dataSource = other.dataSource;
    this.parentDids = other.parentDids == null ? null : new ArrayList<>(other.parentDids);
    this.collectId = other.collectId;
    this.childDids = other.childDids == null ? null : new ArrayList<>(other.childDids);
    this.requeueCount = other.requeueCount;
    this.ingressBytes = other.ingressBytes;
    this.referencedBytes = other.referencedBytes;
    this.totalBytes = other.totalBytes;
    this.stage = other.stage;
    this.flows = other.flows == null ? null : other.flows.stream().map(DeltaFileFlow::new).toList();
    this.annotations = other.annotations == null ? null : new ArrayList<>(other.annotations);
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
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    
    DeltaFile other = (DeltaFile) o;

    return requeueCount == other.requeueCount &&
            ingressBytes == other.ingressBytes &&
            referencedBytes == other.referencedBytes &&
            totalBytes == other.totalBytes &&
            inFlight == other.inFlight &&
            terminal == other.terminal &&
            contentDeletable == other.contentDeletable &&
            version == other.version &&
            Objects.equals(did, other.did) &&
            Objects.equals(name, other.name) &&
            Objects.equals(normalizedName, other.normalizedName) &&
            Objects.equals(dataSource, other.dataSource) &&
            Objects.equals(parentDids, other.parentDids) &&
            Objects.equals(collectId, other.collectId) &&
            Objects.equals(childDids, other.childDids) &&
            Objects.equals(stage, other.stage) &&
            Objects.equals(created, other.created) &&
            Objects.equals(modified, other.modified) &&
            Objects.equals(contentDeleted, other.contentDeleted) &&
            Objects.equals(contentDeletedReason, other.contentDeletedReason) &&
            Objects.equals(egressed, other.egressed) &&
            Objects.equals(filtered, other.filtered) &&
            Objects.equals(replayed, other.replayed) &&
            Objects.equals(replayDid, other.replayDid) &&
            Objects.equals(cacheTime, other.cacheTime) &&
            Objects.equals(schemaVersion, other.schemaVersion) &&
            Objects.equals(new ArrayList<>(flows), new ArrayList<>(other.flows)) &&
            Objects.equals(new ArrayList<>(annotations), new ArrayList<>(other.annotations));
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
      pendingAnnotations.forEach(deltaFileFlow -> deltaFileFlow.removePendingAnnotations(this.annotations.stream().map(Annotation::getKey).collect(Collectors.toSet())));
      updateFlags();
    }
  }

  public void setPendingAnnotations(String flowName, Set<String> expectedAnnotations, OffsetDateTime now) {
    Set<String> pendingAnnotations = getPendingAnnotations(expectedAnnotations);

    flows.stream().filter(flow -> flow.getType() == FlowType.EGRESS && flow.getName().equals(flowName))
            .forEach(deltaFileFlow -> setPendingAnnotations(deltaFileFlow, pendingAnnotations, now));

    updateFlags();
  }

  @Transient
  public Set<String> getPendingAnnotations(Set<String> expectedAnnotations) {
    Set<String> pendingAnnotations = expectedAnnotations != null ? new HashSet<>(expectedAnnotations) : new HashSet<>();

    pendingAnnotations.removeAll(this.annotations.stream().map(Annotation::getKey).collect(Collectors.toSet()));

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
    deltaFileFlow.updateState(now);
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

  public void addAnnotations(Map<String, String> newAnnotations) {
    if (annotations == null) {
      annotations = new ArrayList<>();
    }
    for (Map.Entry<String, String> newAnnotation : newAnnotations.entrySet()) {
      Optional<Annotation> maybeAnnotation = annotations.stream().filter(a -> a.getKey().equals(newAnnotation.getKey())).findFirst();
      if (maybeAnnotation.isPresent()) {
        maybeAnnotation.get().setValue(newAnnotation.getValue());
      } else {
        annotations.add(new Annotation(newAnnotation.getKey(), newAnnotation.getValue()));
      }
    }
  }

  public void addAnnotationsIfAbsent(Map<String, String> metadata) {
    metadata.forEach(this::addAnnotationIfAbsent);
  }

  public void addAnnotationIfAbsent(String key, String value) {
    if (key == null || (annotations != null && annotations.stream().anyMatch(a -> a.getKey().equals(key)))) {
      return;
    }

    if (annotations == null) {
      annotations = new ArrayList<>();
    }
    this.annotations.add(new Annotation(key, value));
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
    return flows.stream().filter(f -> f.getNumber() == flowId && f.getName().equals(flowName)).findFirst().orElse(null);
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

    Set<String> indexedKeys = annotations.stream().map(Annotation::getKey).collect(Collectors.toSet());
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
            .number(flows.stream().mapToInt(DeltaFileFlow::getNumber).max().orElse(0) + 1)
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

    return flow;
  }

  public void setName(String name) {
    this.name = name;
    this.normalizedName = name != null ? name.toLowerCase() : null;
  }

  /**
   * Create the ActionInput that should be sent to an Action
   * @param actionConfiguration Configured action
   * @param flow the flow on which the Action is specified
   * @param action the action
   * @param systemName system name to set in context
   * @param returnAddress the unique address of this core instance
   * @param memo memo to set in the context
   * @return ActionInput containing the ActionConfiguration
   */
  public WrappedActionInput buildActionInput(ActionConfiguration actionConfiguration, DeltaFileFlow flow, Action action, String systemName,
                                             String returnAddress, String memo) {
    WrappedActionInput actionInput = buildActionInput(actionConfiguration, flow, List.of(), action, systemName, returnAddress, memo);
    actionInput.setDeltaFileMessages(List.of(new DeltaFileMessage(flow.getMetadata(), flow.lastContent().stream().map(c -> new Content(c.getName(), c.getMediaType(), c.getSegments())).toList())));
    return actionInput;
  }

  /**
   * Create the ActionInput that should be sent to an Action
   * @param actionConfiguration Configured action
   * @param flow the flow on which the Action is specified
   * @param collectedDids the list of dids that were combined to create the child, or an empty list
   * @param action the action
   * @param systemName system name to set in context
   * @param returnAddress the unique address of this core instance
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
                    .flowId(flow.getNumber())
                    .actionName(action.getName())
                    .actionId(action.getNumber())
                    .did(did)
                    .deltaFileName(name)
                    .collectedDids(Objects.requireNonNullElseGet(collectedDids, List::of))
                    .collect(actionConfiguration.getCollect())
                    .systemName(systemName)
                    .memo(memo)
                    .build())
            .deltaFile(this)
            .actionParams(actionConfiguration.getParameters())
            .returnAddress(returnAddress)
            .actionCreated(action.getCreated())
            .coldQueued(action.getState() == ActionState.COLD_QUEUED)
            .build();
  }

  public Map<String, String> annotationMap() {
    return annotations.stream().collect(Collectors.toMap(Annotation::getKey, Annotation::getValue));
  }

  public List<String> egressFlowNames() {
    return flows.stream().filter(f -> f.getType() == FlowType.EGRESS).map(DeltaFileFlow::getName).distinct().toList();
  }
}
