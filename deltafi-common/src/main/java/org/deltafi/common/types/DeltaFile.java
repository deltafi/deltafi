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
import org.jetbrains.annotations.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.util.MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(builderMethodName = "newBuilder")
@Document
public class DeltaFile {
  @Id
  private String did;
  private List<String> parentDids;
  private List<String> childDids;
  private int requeueCount;
  private long ingressBytes = 0;
  private long referencedBytes = 0;
  private long totalBytes = 0;
  private DeltaFileStage stage;
  private List<Action> actions;
  private SourceInfo sourceInfo;
  private List<ProtocolLayer> protocolStack;
  private List<Domain> domains;
  private Map<String, String> indexedMetadata = new HashMap<>();
  private Set<String> indexedMetadataKeys = new HashSet<>();
  private List<Enrichment> enrichment;
  @Builder.Default
  private List<Egress> egress = new ArrayList<>();
  private List<FormattedData> formattedData;
  private OffsetDateTime created;
  private OffsetDateTime modified;
  private OffsetDateTime contentDeleted;
  private String contentDeletedReason;
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
  private boolean joined;

  @Version
  @Getter
  @Setter
  @JsonIgnore
  private long version;

  public void queueAction(String name) {
    Optional<Action> maybeAction = actionNamed(name);
    if (maybeAction.isPresent()) {
      setActionState(maybeAction.get(), ActionState.QUEUED, null, null);
    } else {
      queueNewAction(name);
    }
  }

  public void queueNewAction(String name) {
    OffsetDateTime now = OffsetDateTime.now();
    getActions().add(Action.newBuilder()
            .name(name)
            .state(ActionState.QUEUED)
            .created(now)
            .queued(now)
            .modified(now)
            .attempt(1 + getLastAttemptNum(name))
            .build());
  }

  private int getLastAttemptNum(String name) {
    Optional<Action> action = getActions().stream()
            .filter(a -> a.getName().equals(name) && retried(a))
            .reduce((first, second) -> second);
    return action.map(Action::getAttempt).orElse(0);
  }

  /* Get the most recent action with the given name */
  public Optional<Action> actionNamed(String name) {
    return getActions().stream()
            .filter(a -> a.getName().equals(name) && !retried(a))
            .reduce((first, second) -> second);
  }

  public boolean isNewAction(String name) {
    return actionNamed(name).isEmpty();
  }

  public void queueActionsIfNew(List<String> actions) {
    actions.stream().filter(this::isNewAction).forEach(this::queueNewAction);
  }

  public void completeAction(ActionEventInput event) {
    completeAction(event.getAction(), event.getStart(), event.getStop());
  }

  public void completeAction(String name, OffsetDateTime start, OffsetDateTime stop) {
    getActions().stream()
            .filter(action -> action.getName().equals(name) && !terminalState(action.getState()))
            .forEach(action -> setActionState(action, ActionState.COMPLETE, start, stop));
  }

  public void filterAction(ActionEventInput event, String filterMessage) {
    getActions().stream()
            .filter(action -> action.getName().equals(event.getAction()) && !terminalState(action.getState()))
            .forEach(action -> setFilteredActionState(action, event.getStart(), event.getStop(), filterMessage));
  }

  public void splitAction(ActionEventInput event) {
    getActions().stream()
            .filter(action -> action.getName().equals(event.getAction()) && !terminalState(action.getState()))
            .forEach(action -> setActionState(action, ActionState.SPLIT, event.getStart(), event.getStop()));
  }

  public void errorAction(ActionEventInput event) {
    errorAction(event.getAction(), event.getStart(), event.getStop(), event.getError().getCause(),
            event.getError().getContext());
  }

  public void errorAction(ActionEventInput event, String policyName, Integer delay) {
    setNextAutoResumeReason(policyName);
    errorAction(event.getAction(), event.getStart(), event.getStop(), event.getError().getCause(),
            event.getError().getContext(), event.getStop().plusSeconds(delay));
  }

  public void errorAction(ActionEventInput event, String errorCause, String errorContext) {
    errorAction(event.getAction(), event.getStart(), event.getStop(), errorCause, errorContext, null);
  }

  public void errorAction(String name, OffsetDateTime start, OffsetDateTime stop, String errorCause, String errorContext) {
    errorAction(name, start, stop, errorCause, errorContext, null);
  }

  public void errorAction(String name, OffsetDateTime start, OffsetDateTime stop, String errorCause, String errorContext, OffsetDateTime nextAutoResume) {
    getActions().stream()
            .filter(action -> action.getName().equals(name) && !terminalState(action.getState()))
            .forEach(action -> setActionState(action, ActionState.ERROR, start, stop, errorCause, errorContext, nextAutoResume));
  }

  public List<String> retryErrors() {
    List<Action> actionsToRetry = getActions().stream()
            .filter(action -> action.getState().equals(ActionState.ERROR))
            .toList();

    // this must be separate from the above stream since it mutates the original list
    actionsToRetry.forEach(action -> action.setState(ActionState.RETRIED));
    setNextAutoResume(null);

    return actionsToRetry.stream().map(Action::getName).toList();
  }

  private void setActionState(Action action, ActionState actionState, OffsetDateTime start, OffsetDateTime stop) {
    setActionState(action, actionState, start, stop, null, null, null);
  }

  private void setFilteredActionState(Action action, OffsetDateTime start, OffsetDateTime stop, String filteredCause) {
    action.setFilteredCause(filteredCause);
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
    return getActions().stream().filter(action -> action.getState().equals(ActionState.QUEUED)).map(Action::getName).toList();
  }

  public Map<String, Domain> domainMap() {
    return getDomains().stream().collect(Collectors.toMap(Domain::getName, Function.identity()));
  }

  public Map<String, Enrichment> enrichmentMap() {
    return getEnrichment().stream().collect(Collectors.toMap(Enrichment::getName, Function.identity()));
  }

  public void addDomain(@NotNull String domainKey, String domainValue, @NotNull String mediaType) {
    Optional<Domain> domain = getDomains().stream().filter(d -> d.getName().equals(domainKey)).findFirst();
    if (domain.isPresent()) {
      domain.get().setValue(domainValue);
    } else {
      getDomains().add(new Domain(domainKey, domainValue, mediaType));
    }
  }

  public boolean hasDomains(List<String> domains) {
    return domains.stream().allMatch(domain -> getDomains().stream().anyMatch(d -> d.getName().equals(domain)));
  }

  public void addIndexedMetadata(Map<String, String> metadata) {
    if (null == metadata) {
      return;
    }

    this.indexedMetadata.putAll(metadata);
    this.indexedMetadataKeys.addAll(metadata.keySet());
  }

  public void addIndexedMetadataIfAbsent(Map<String, String> metadata) {
    metadata.forEach(this::addIndexedMetadataIfAbsent);
  }

  public void addIndexedMetadataIfAbsent(String key, String value) {
    if (null == key || indexedMetadata.containsKey(key)) {
      return;
    }

    this.indexedMetadata.put(key, value);
    this.indexedMetadataKeys.add(key);
  }

  public void addEgressFlow(@NotNull String flow) {
    if (!getEgress().stream().map(Egress::getFlow).toList().contains(flow)) {
      getEgress().add(new Egress(flow));
    }
  }

  @SuppressWarnings("unused")
  public Enrichment getEnrichment(String enrichment) {
    return getEnrichment().stream().filter(e -> e.getName().equals(enrichment)).findFirst().orElse(null);
  }

  public void addEnrichment(@NotNull String enrichmentKey, String enrichmentValue) {
    addEnrichment(enrichmentKey, enrichmentValue, APPLICATION_OCTET_STREAM_VALUE);
  }

  public void addEnrichment(@NotNull String enrichmentKey, String enrichmentValue, @NotNull String mediaType) {
    Optional<Enrichment> enrichment = getEnrichment().stream().filter(d -> d.getName().equals(enrichmentKey)).findFirst();
    if (enrichment.isPresent()) {
      enrichment.get().setValue(enrichmentValue);
    } else {
      getEnrichment().add(new Enrichment(enrichmentKey, enrichmentValue, mediaType));
    }
  }

  public boolean hasEnrichments(List<String> enrichments) {
    return enrichments.stream().allMatch(enrichment -> getEnrichment().stream().anyMatch(e -> e.getName().equals(enrichment)));
  }

  public boolean hasErroredAction() {
    return getActions().stream().anyMatch(action -> action.getState().equals(ActionState.ERROR));
  }

  public boolean hasPendingActions() {
    return getActions().stream().anyMatch(action -> action.getState().equals(ActionState.QUEUED));
  }

  public boolean hasFilteredAction() {
    return getActions().stream().anyMatch(action -> action.getState().equals(ActionState.FILTERED));
  }

  public boolean hasSplitAction() {
    return getActions().stream().anyMatch(action -> action.getState().equals(ActionState.SPLIT));
  }

  public boolean noPendingAction(String name) {
    return getActions().stream().noneMatch(action -> action.getName().equals(name) && !terminalState(action.getState()));
  }

  private boolean terminalState(ActionState actionState) {
    return !actionState.equals(ActionState.QUEUED);
  }

  private boolean retried(Action action) {
    return action.getState().equals(ActionState.RETRIED);
  }

  public boolean hasTerminalAction(String name) {
    return getActions().stream().anyMatch(action -> action.getName().equals(name) && !retried(action) && terminalState(action.getState()));
  }

  public boolean hasCompletedAction(String name) {
    return getActions().stream().anyMatch(action -> action.getName().equals(name) && action.getState().equals(ActionState.COMPLETE));
  }

  public boolean hasCompletedActions(List<String> names) {
    return names.stream().allMatch(this::hasCompletedAction);
  }

  public String sourceMetadata(String key) {
    return getSourceInfo().getMetadata().get(key);
  }

  public String sourceMetadata(String key, String defaultValue) {
    return getSourceInfo().getMetadata().getOrDefault(key, defaultValue);
  }

  @JsonIgnore
  public ProtocolLayer getLastProtocolLayer() {
    return (Objects.isNull(getProtocolStack()) || getProtocolStack().isEmpty()) ? null : getProtocolStack().get(getProtocolStack().size() - 1);
  }

  @JsonIgnore
  public @NotNull List<Content> getLastProtocolLayerContent() {
    if (Objects.isNull(getLastProtocolLayer()) || Objects.isNull(getLastProtocolLayer().getContent())) {
      return Collections.emptyList();
    }

    return getLastProtocolLayer().getContent();
  }

  @JsonIgnore
  @SuppressWarnings("unused")
  public @NotNull Map<String, String> getLastProtocolLayerMetadata() {
    if (Objects.isNull(getLastProtocolLayer()) || Objects.isNull(getLastProtocolLayer().getMetadata())) {
      return Collections.emptyMap();
    }

    return getLastProtocolLayer().getMetadata();
  }

  public @NotNull List<Domain> getDomains() {
    return domains == null ? Collections.emptyList() : domains;
  }

  public @NotNull List<Enrichment> getEnrichment() {
    return enrichment == null ? Collections.emptyList() : enrichment;
  }

  public @NotNull List<Egress> getEgress() {
    if (egress == null) egress = new ArrayList<>();
    return egress;
  }

  public DeltaFile forQueue(String actionName) {
    DeltaFileBuilder builder = DeltaFile.newBuilder()
            .did(getDid())
            .sourceInfo(getSourceInfo())
            .domains(getDomains())
            .enrichment(getEnrichment())
            .formattedData(getFormattedData().stream()
                    .filter(f -> f.getEgressActions().contains(actionName) ||
                            (Objects.nonNull(f.getValidateActions()) && f.getValidateActions().contains(actionName)))
                    .toList());

    if (!getProtocolStack().isEmpty()) {
      builder.protocolStack(Collections.singletonList(getLastProtocolLayer()));
    }

    return builder.build();
  }

  public List<Segment> referencedSegments() {
    List<Segment> segments = getProtocolStack().stream()
            .flatMap(p -> p.getContent().stream()).map(Content::getContentReference)
            .flatMap(c -> c.getSegments().stream())
            .collect(Collectors.toList());
    segments.addAll(getFormattedData().stream()
            .map(FormattedData::getContentReference)
            .flatMap(f -> f.getSegments().stream())
            .toList());
    return segments;
  }

  public List<Segment> storedSegments() {
    List<Segment> segments = getProtocolStack().stream()
            .flatMap(p -> p.getContent().stream()).map(Content::getContentReference)
            .flatMap(c -> c.getSegments().stream())
            .filter(s -> s.getDid().equals(getDid()))
            .collect(Collectors.toList());
    segments.addAll(getFormattedData().stream()
            .map(FormattedData::getContentReference)
            .flatMap(f -> f.getSegments().stream())
            .filter(s -> s.getDid().equals(getDid()))
            .toList());
    return segments;
  }

  public void recalculateBytes() {
    setReferencedBytes(Segment.calculateTotalSize(referencedSegments()));
    setTotalBytes(Segment.calculateTotalSize(storedSegments()));
  }

  public void cancelQueuedActions() {
    OffsetDateTime now = OffsetDateTime.now();
    getActions().stream()
            .filter(a -> a.getState().equals(ActionState.QUEUED))
            .forEach(a -> {
              a.setState(ActionState.CANCELLED);
              a.setModified(now);
            });
    setModified(now);
  }

  public void incrementRequeueCount() {
    ++requeueCount;
  }

  public boolean inactiveStage() {
    return getStage() == DeltaFileStage.COMPLETE || getStage() == DeltaFileStage.ERROR || getStage() == DeltaFileStage.CANCELLED;
  }

  public static class DeltaFileBuilder {
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Map<String, String> indexedMetadata = new HashMap<>();

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Set<String> indexedMetadataKeys = new HashSet<>();

    public DeltaFileBuilder indexedMetadata(Map<String, String> indexedMetadata) {
      this.indexedMetadata = indexedMetadata;
      this.indexedMetadataKeys = indexedMetadata.keySet();

      return this;
    }
  }
}
