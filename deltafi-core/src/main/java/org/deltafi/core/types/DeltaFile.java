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

import com.fasterxml.uuid.Generators;
import jakarta.persistence.*;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.*;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.*;
import org.deltafi.core.exceptions.UnexpectedFlowException;
import org.deltafi.core.types.hibernate.StringArrayType;
import org.deltafi.core.types.hibernate.UUIDArrayType;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
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
@Table(name = "delta_files")
@NamedEntityGraph(
        name = "deltaFile.withFlowsAndActions",
        attributeNodes = {
                @NamedAttributeNode(value = "flows"),
                @NamedAttributeNode("annotations")
        }
)
@DynamicUpdate
@EqualsAndHashCode
public class DeltaFile {
  @Id
  @Builder.Default
  private UUID did = Generators.timeBasedEpochGenerator().generate();
  private String name;
  private String dataSource;
  @Type(UUIDArrayType.class)
  @Column(columnDefinition = "uuid[]")
  @Builder.Default
  private List<UUID> parentDids = new ArrayList<>();
  private UUID joinId;
  @Type(UUIDArrayType.class)
  @Column(columnDefinition = "uuid[]")
  @Builder.Default
  private List<UUID> childDids = new ArrayList<>();
  @Builder.Default
  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  @Fetch(FetchMode.SELECT)
  @JoinColumn(name = "delta_file_id", nullable = false, updatable = false)
  @OrderBy("number ASC")
  private Set<DeltaFileFlow> flows = new LinkedHashSet<>();
  private int requeueCount;
  private long ingressBytes;
  private long referencedBytes;
  private long totalBytes;
  @Enumerated(EnumType.STRING)
  @Column(columnDefinition = "df_stage_enum", nullable = false)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Builder.Default
  private DeltaFileStage stage = DeltaFileStage.IN_FLIGHT;
  @Builder.Default
  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  @Fetch(FetchMode.SELECT)
  @JoinColumn(name = "delta_file_id", nullable = false, updatable = false)
  private Set<Annotation> annotations = new LinkedHashSet<>();
  private OffsetDateTime created;
  private OffsetDateTime modified;
  private Boolean egressed;
  private Boolean filtered;
  private OffsetDateTime replayed;
  private UUID replayDid;

  @Builder.Default
  private boolean terminal = false;

  @Builder.Default
  private boolean pinned = false;

  @Builder.Default
  private boolean contentDeletable = false;
  private OffsetDateTime contentDeleted;
  private String contentDeletedReason;

  @Type(UUIDArrayType.class)
  @Column(columnDefinition = "uuid[]")
  @Builder.Default
  private List<UUID> contentObjectIds = new ArrayList<>();

  @Type(StringArrayType.class)
  @Column(columnDefinition = "text[]")
  @Builder.Default
  private List<String> topics = new ArrayList<>();

  @Type(StringArrayType.class)
  @Column(columnDefinition = "text[]")
  @Builder.Default
  private List<String> transforms = new ArrayList<>();

  @Type(StringArrayType.class)
  @Column(columnDefinition = "text[]")
  @Builder.Default
  private List<String> dataSinks = new ArrayList<>();

  @Builder.Default
  private Boolean paused = false;

  @Builder.Default
  private Boolean waitingForChildren = false;

  @Version
  @EqualsAndHashCode.Exclude
  private long version;

  @Builder.Default
  @Transient
  @EqualsAndHashCode.Exclude
  private OffsetDateTime cacheTime = null;

  public DeltaFile(DeltaFile other) {
    this.did = other.did;
    this.name = other.name;
    this.dataSource = other.dataSource;
    this.parentDids = other.parentDids == null ? null : new ArrayList<>(other.parentDids);
    this.joinId = other.joinId;
    this.childDids = other.childDids == null ? null : new ArrayList<>(other.childDids);
    this.requeueCount = other.requeueCount;
    this.ingressBytes = other.ingressBytes;
    this.referencedBytes = other.referencedBytes;
    this.totalBytes = other.totalBytes;
    this.stage = other.stage;
    this.flows = other.flows == null ? null : other.flows.stream().map(DeltaFileFlow::new).collect(Collectors.toSet());
    this.annotations = other.annotations == null ? null : new LinkedHashSet<>(other.annotations);
    this.created = other.created;
    this.modified = other.modified;
    this.egressed = other.egressed;
    this.filtered = other.filtered;
    this.replayed = other.replayed;
    this.replayDid = other.replayDid;
    this.terminal = other.terminal;
    this.pinned = other.pinned;
    this.contentDeletable = other.contentDeletable;
    this.contentDeleted = other.contentDeleted;
    this.contentDeletedReason = other.contentDeletedReason;
    this.version = other.version;
    this.cacheTime = other.cacheTime;
    this.contentObjectIds = other.contentObjectIds;
    this.topics = new ArrayList<>(other.topics);
    this.transforms = new ArrayList<>(other.transforms);
    this.dataSinks = new ArrayList<>(other.dataSinks);
    this.paused = other.paused;
    this.waitingForChildren = other.waitingForChildren;
  }

  @EqualsAndHashCode.Include(replaces = "flows")
  @SuppressWarnings("unused")
  private Set<DeltaFileFlow> getFlowsForEquality() {
    return new LinkedHashSet<>(flows);
  }

  @EqualsAndHashCode.Include(replaces = "annotations")
  @SuppressWarnings("unused")
  private Set<Annotation> getAnnotationsForEquality() {
    return new LinkedHashSet<>(annotations);
  }

  public void setStage(DeltaFileStage stage) {
    this.stage = stage;
    updateFlags();
    updateContentObjectIds();
  }

  public void updateFlags() {
    terminal = stage != DeltaFileStage.IN_FLIGHT && unackErrorFlows().isEmpty() && !hasPendingAnnotations() && !waitingForChildren;
    contentDeletable = terminal && contentDeleted == null && totalBytes > 0;
    filtered = flows.stream().anyMatch(f -> f.getState() == DeltaFileFlowState.FILTERED);
    // only set paused if all flows are terminal or paused
    paused = stage == DeltaFileStage.IN_FLIGHT &&
            flows.stream().anyMatch(f -> f.getState() == DeltaFileFlowState.PAUSED) &&
            flows.stream().allMatch(f -> f.terminal() || f.getState() == DeltaFileFlowState.PAUSED);
  }

  public void updateContentObjectIds() {
    contentObjectIds = storedSegments().stream()
            .map(Segment::getUuid)
            .distinct()
            .toList();
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

  public void setPendingAnnotations(String flowName, Set<String> expectedAnnotations) {
    Set<String> pendingAnnotations = getPendingAnnotations(expectedAnnotations);

    flows.stream().filter(flow -> flow.getType() == FlowType.DATA_SINK && flow.getName().equals(flowName))
            .forEach(deltaFileFlow -> setPendingAnnotations(deltaFileFlow, pendingAnnotations));

    updateFlags();
  }

  @Transient
  public Set<String> getPendingAnnotations(Set<String> expectedAnnotations) {
    Set<String> pendingAnnotations = expectedAnnotations != null ? new HashSet<>(expectedAnnotations) : new HashSet<>();

    pendingAnnotations.removeAll(this.annotations.stream().map(Annotation::getKey).collect(Collectors.toSet()));

    return pendingAnnotations;
  }

  private void setPendingAnnotations(DeltaFileFlow flow, Set<String> expectedAnnotations) {
    flow.setPendingAnnotations(expectedAnnotations);
    flow.updateState();
  }

  public List<DeltaFileFlow> erroredFlows() {
    return flows.stream()
            .filter(f -> f.getState() == DeltaFileFlowState.ERROR)
            .toList();
  }

  /**
   * Find all flows that were joining on the given JoinId and update the
   * joining actions to a state of joined. Update the state of each modified
   * dataSource and then update this DeltaFiles state.
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
    deltaFileFlow.updateState();
  }

  public void errorJoinAction(UUID joinId, String actionName, OffsetDateTime now, String reason) {
    joiningFlows(joinId).forEach(f -> errorJoinAction(f, actionName, now, reason));
    updateState(now);
  }

  private void errorJoinAction(DeltaFileFlow deltaFileFlow, String actionName, OffsetDateTime now, String reason) {
    deltaFileFlow.getActions().stream()
            .filter(a -> a.getName().equals(actionName) && a.getState() == ActionState.JOINING)
            .forEach(a -> a.error(now, now, now, "Failed join", reason));
    deltaFileFlow.updateState();
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
      annotations = new LinkedHashSet<>();
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
      annotations = new LinkedHashSet<>();
    }
    this.annotations.add(new Annotation(key, value));
  }

  public boolean hasPendingActions() {
    return flows.stream().anyMatch(flow -> !flow.terminal());
  }

  public boolean hasPendingAnnotations() {
    return flows.stream().anyMatch(DeltaFileFlow::hasPendingAnnotations);
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

  public DeltaFileFlow getFlow(UUID flowId) {
    return flows.stream().filter(f -> f.getId().equals(flowId)).findFirst().orElse(null);
  }

  public DeltaFileFlow getPendingFlow(String flowName, UUID flowId) {
    DeltaFileFlow flow = getFlow(flowId);
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

  public boolean noActiveFlows() {
    return inactiveStage() || flows.stream().noneMatch(f -> f.getState() == DeltaFileFlowState.IN_FLIGHT);
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
    updateContentObjectIds();
    updateFlowArrays();
  }

  public void updateFlowArrays() {
    topics = flows.stream()
            .flatMap(f -> f.getPublishTopics().stream())
            .distinct()
            .toList();

    transforms = flows.stream()
            .filter(f -> f.getType() == FlowType.TRANSFORM)
            .map(DeltaFileFlow::getName)
            .distinct()
            .toList();

    dataSinks = flows.stream()
            .filter(f -> f.getType() == FlowType.DATA_SINK)
            .map(DeltaFileFlow::getName)
            .distinct()
            .toList();
  }

  public DeltaFileFlow addFlow(FlowDefinition flowDefinition, DeltaFileFlow previousFlow, OffsetDateTime now) {
    return addFlow(flowDefinition, previousFlow, Set.of(), now);
  }

  public DeltaFileFlow addFlow(FlowDefinition flowDefinition, DeltaFileFlow previousFlow, Set<String> subscribedTopics, OffsetDateTime now) {
    DeltaFileFlow flow = DeltaFileFlow.builder()
            .flowDefinition(flowDefinition)
            .number(flows.stream().mapToInt(DeltaFileFlow::getNumber).max().orElse(0) + 1)
            .state(DeltaFileFlowState.IN_FLIGHT)
            .created(now)
            .modified(now)
            .input(DeltaFileFlowInput.builder()
                    .metadata(previousFlow.getMetadata())
                    .content(previousFlow.lastContent())
                    .topics(subscribedTopics)
                    .ancestorIds(Stream.concat(Stream.of(previousFlow.getNumber()), previousFlow.getInput().getAncestorIds().stream()).collect(Collectors.toList()))
                    .build())
            .depth(previousFlow.getDepth() + 1)
            .testMode(previousFlow.isTestMode())
            .testModeReason(previousFlow.getTestModeReason())
            .build();
    flows.add(flow);
    updateFlowArrays();

    return flow;
  }

  /**
   * Create the ActionInput that should be sent to an Action
   * @param actionConfiguration Configured action
   * @param flow the dataSource on which the Action is specified
   * @param action the action
   * @param systemName system name to set in context
   * @param returnAddress the unique address of this core instance
   * @param memo memo to set in the context
   * @return ActionInput containing the ActionConfiguration
   */
  public WrappedActionInput buildActionInput(ActionConfiguration actionConfiguration, DeltaFileFlow flow, Action action, String systemName,
                                             String returnAddress, String memo) {
    WrappedActionInput actionInput = buildActionInput(actionConfiguration, flow, List.of(), action, systemName, returnAddress, memo);
    actionInput.setDeltaFileMessages(List.of(new DeltaFileMessage(flow.getMetadata(), flow.lastContent().stream().map(Content::copy).toList())));
    actionInput.setTemplated(actionConfiguration.isTemplated());
    actionInput.setParameterSchema(actionConfiguration.getParameterSchema());
    return actionInput;
  }

  /**
   * Create the ActionInput that should be sent to an Action
   * @param actionConfiguration Configured action
   * @param flow the dataSource on which the Action is specified
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
            .templated(actionConfiguration.isTemplated())
            .parameterSchema(actionConfiguration.getParameterSchema())
            .build();
  }

  public Map<String, String> annotationMap() {
    return annotations.stream().collect(Collectors.toMap(Annotation::getKey, Annotation::getValue));
  }

  public DeltaFileFlow firstFlow() {
    return flows.stream()
            .min(Comparator.comparingInt(DeltaFileFlow::getNumber))
            .orElse(null);
  }

  public DeltaFileFlow lastFlow() {
    return flows.stream()
            .max(Comparator.comparingInt(DeltaFileFlow::getNumber))
            .orElse(null);
  }
}
