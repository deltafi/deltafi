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
import com.fasterxml.uuid.Generators;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.*;
import org.deltafi.core.exceptions.UnexpectedFlowException;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Type;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "delta_files", indexes = {
        @Index(name = "idx_created", columnList = "created, stage, data_source, normalized_name, egressed, filtered, terminal, ingress_bytes"),
        @Index(name = "idx_modified", columnList = "modified, stage, data_source, normalized_name, egressed, filtered, terminal, ingress_bytes")
})
@NamedEntityGraph(
        name = "deltaFile.withFlowsAndActions",
        attributeNodes = {
                @NamedAttributeNode(value = "flows", subgraph = "flows"),
                @NamedAttributeNode("annotations")
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "flows",
                        attributeNodes = {
                                @NamedAttributeNode("actions")
                        }
                )
        }
)
@DynamicUpdate
public class DeltaFile {
  @Id
  @Builder.Default
  private UUID did = Generators.timeBasedEpochGenerator().generate();
  private String name;
  private String normalizedName;
  private String dataSource;
  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @Builder.Default
  private List<UUID> parentDids = new ArrayList<>();
  private UUID joinId;
  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @Builder.Default
  private List<UUID> childDids = new ArrayList<>();
  @Builder.Default
  @OneToMany(mappedBy = "deltaFile", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  @JsonManagedReference
  @OrderBy("number ASC")
  private List<DeltaFileFlow> flows = new ArrayList<>();
  private int requeueCount;
  private long ingressBytes;
  private long referencedBytes;
  private long totalBytes;
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private DeltaFileStage stage = DeltaFileStage.IN_FLIGHT;
  @Builder.Default
  @OneToMany(mappedBy = "deltaFile", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
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
  private boolean terminal = false;
  @Builder.Default
  private boolean contentDeletable = false;

  @Version
  private long version;

  @Builder.Default
  @Transient
  private OffsetDateTime cacheTime = null;

  public DeltaFile(DeltaFile other) {
    this.did = other.did;
    this.name = other.name;
    this.normalizedName = other.normalizedName;
    this.dataSource = other.dataSource;
    this.parentDids = other.parentDids == null ? null : new ArrayList<>(other.parentDids);
    this.joinId = other.joinId;
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
    this.terminal = other.terminal;
    this.contentDeletable = other.contentDeletable;
    this.version = other.version;
    this.cacheTime = other.cacheTime;
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
            terminal == other.terminal &&
            contentDeletable == other.contentDeletable &&
            version == other.version &&
            Objects.equals(did, other.did) &&
            Objects.equals(name, other.name) &&
            Objects.equals(normalizedName, other.normalizedName) &&
            Objects.equals(dataSource, other.dataSource) &&
            Objects.equals(parentDids, other.parentDids) &&
            Objects.equals(joinId, other.joinId) &&
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
            Objects.equals(new ArrayList<>(flows), new ArrayList<>(other.flows)) &&
            Objects.equals(new ArrayList<>(annotations).stream().sorted().toList(), new ArrayList<>(other.annotations).stream().sorted().toList());
  }

  @Override
  public int hashCode() {
    return Objects.hash(did, name, normalizedName, dataSource, parentDids, joinId, childDids, requeueCount, ingressBytes, referencedBytes, totalBytes, stage, created, modified, contentDeleted, contentDeletedReason, egressed, filtered, replayed, replayDid, terminal, contentDeletable, version, cacheTime, new ArrayList<>(flows), new ArrayList<>(annotations));
  }

  public void setStage(DeltaFileStage stage) {
      this.stage = stage;
      updateFlags();
  }

  public void updateFlags() {
    terminal = stage != DeltaFileStage.IN_FLIGHT && unackErrorFlows().isEmpty() && pendingAnnotationFlows().isEmpty();
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
   * Find all flows that were joining on the given JoinId and update the
   * joining actions to a state of joined. Update the state of each modified
   * flow and then update this DeltaFiles state.
   * @param joinId did of the child DeltaFile that joined the data
   * @param actionName name of the action to mark as Joined
   * @param start start time of the action
   * @param stop stop time of the action
   * @param now current time
   */
  public void joinedAction(UUID joinId, String actionName, OffsetDateTime start, OffsetDateTime stop, OffsetDateTime now) {
    joiningFlows(joinId).forEach(f -> joinAction(f, actionName, start, stop, now));
    updateState(now);
  }

  private void joinAction(DeltaFileFlow deltaFileFlow, String actionName, OffsetDateTime start, OffsetDateTime stop, OffsetDateTime now) {
    deltaFileFlow.getActions().stream()
            .filter(a -> a.getName().equals(actionName) && a.getState() == ActionState.JOINING)
            .forEach(action -> action.changeState(ActionState.JOINED, start, stop, now));
    deltaFileFlow.updateState(now);
  }

  public void timeoutJoinAction(UUID joinId, String actionName, OffsetDateTime now, String reason) {
    joiningFlows(joinId).forEach(f -> timeoutJoinAction(f, actionName, now, reason));
    updateState(now);
  }

  private void timeoutJoinAction(DeltaFileFlow deltaFileFlow, String actionName, OffsetDateTime now, String reason) {
    deltaFileFlow.getActions().stream()
            .filter(a -> a.getName().equals(actionName) && a.getState() == ActionState.JOINING)
            .forEach(a -> a.error(now, now, now, "Failed join", reason));
    deltaFileFlow.updateState(now);
  }

  private Stream<DeltaFileFlow> joiningFlows(UUID joinId) {
    return flows.stream().filter(flow -> joinedWithId(flow, joinId));
  }

  private boolean joinedWithId(DeltaFileFlow flow, UUID joinId) {
    return joinId.equals(flow.getJoinId()) && !flow.terminal();
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
        annotations.add(new Annotation(newAnnotation.getKey(), newAnnotation.getValue(), this));
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
    this.annotations.add(new Annotation(key, value, this));
  }

  public boolean hasPendingActions() {
    return flows.stream().anyMatch(flow -> !flow.terminal());
  }

  public boolean hasErrors() {
    return flows.stream().anyMatch(flow -> flow.getState() == DeltaFileFlowState.ERROR);
  }

  public boolean hasJoiningAction() {
    return flows.stream().anyMatch(f -> f.hasActionInState(ActionState.JOINING));
  }

  public DeltaFileFlow getFlow(String flowName) {
    return flows.stream().filter(f -> f.getName().equals(flowName)).findFirst().orElse(null);
  }

  public DeltaFileFlow getFlow(String flowName, UUID flowId) {
    return flows.stream().filter(f -> f.getId().equals(flowId) && f.getName().equals(flowName)).findFirst().orElse(null);
  }

  public DeltaFileFlow getPendingFlow(String flowName, UUID flowId) {
    DeltaFileFlow flow = getFlow(flowName, flowId);
    if (flow == null || flow.terminal()) {
      throw new UnexpectedFlowException(flowName, flowId, did, flow != null);
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
            .deltaFile(this)
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
   * @param joinedDids the list of dids that were combined to create the child, or an empty list
   * @param action the action
   * @param systemName system name to set in context
   * @param returnAddress the unique address of this core instance
   * @param memo memo to set in the context
   * @return ActionInput containing the ActionConfiguration
   */
  public WrappedActionInput buildActionInput(ActionConfiguration actionConfiguration, DeltaFileFlow flow, List<UUID> joinedDids, Action action, String systemName,
                                             String returnAddress, String memo) {
    Map<String, Object> actionParameters = actionConfiguration.getParameters() == null ? Collections.emptyMap() : actionConfiguration.getParameters();
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
                    .joinedDids(Objects.requireNonNullElseGet(joinedDids, List::of))
                    .join(actionConfiguration.getJoin())
                    .systemName(systemName)
                    .memo(memo)
                    .build())
            .deltaFile(this)
            .actionParams(actionParameters)
            .returnAddress(returnAddress)
            .actionCreated(action.getCreated())
            .coldQueued(action.getState() == ActionState.COLD_QUEUED)
            .build();
  }

  public Map<String, String> annotationMap() {
    return annotations.stream().collect(Collectors.toMap(Annotation::getKey, Annotation::getValue));
  }

  @Transient
  public List<String> getEgressFlows() {
    return flows.stream().filter(f -> f.getType() == FlowType.EGRESS).map(DeltaFileFlow::getName).distinct().toList();
  }
}
