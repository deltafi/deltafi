package org.deltafi.core.domain.api.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.deltafi.core.domain.api.converters.KeyValueConverter;
import org.deltafi.core.domain.delete.DeleteConstants;
import org.deltafi.core.domain.generated.types.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.deltafi.core.domain.api.Constants.ERROR_DOMAIN;

@Document
@CompoundIndex(name = "dispatch_index", def = "{'actions.name': 1, 'actions.state': 1, 'actions.modified': 1}")
@CompoundIndex(name = "completed_before_index", def="{'stage': 1, 'modified': 1, 'sourceInfo.flow': 1}")
@CompoundIndex(name = "created_before_index", def="{'created': 1, 'sourceInfo.flow': 1}")
@CompoundIndex(name = "modified_before_index", def="{'modified': 1, 'sourceInfo.flow': 1}")
public class DeltaFile extends org.deltafi.core.domain.generated.types.DeltaFile {

    @Version
    private long version;

    @Id
    @Override
    public String getDid() {
        return super.getDid();
    }

    @SuppressWarnings("unused")
    public long getVersion() {
        return version;
    }

    @SuppressWarnings("unused")
    public void setVersion(long version) {
        this.version = version;
    }

    public void queueAction(String name) {
        Optional<Action> maybeAction = actionNamed(name);
        if (maybeAction.isPresent()) {
            setActionState(maybeAction.get(), ActionState.QUEUED);
        } else {
            queueNewAction(name);
        }

    }

    public void queueNewAction(String name) {
        OffsetDateTime now = OffsetDateTime.now();
        getActions().add(Action.newBuilder().name(name).state(ActionState.QUEUED).created(now).modified(now).build());
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

    public List<String> queueActionsIfNew(List<String> actions) {
        List<String> newActions = actions.stream().filter(this::isNewAction).collect(Collectors.toList());
        newActions.forEach(this::queueNewAction);
        return newActions;
    }

    public void completeAction(String name) {
        getActions().stream()
                .filter(action -> action.getName().equals(name) && !terminalState(action.getState()))
                .forEach(action -> setActionState(action, ActionState.COMPLETE));
    }

    public void filterAction(String name, String filterMessage) {
        getActions().stream()
                .filter(action -> action.getName().equals(name) && !terminalState(action.getState()))
                .forEach(action -> setActionState(action, ActionState.FILTERED, filterMessage, null));
    }

    public void splitAction(String name) {
        getActions().stream()
                .filter(action -> action.getName().equals(name) && !terminalState(action.getState()))
                .forEach(action -> setActionState(action, ActionState.SPLIT, null, null));
    }

    public void errorAction(String name, String errorCause, String errorContext) {
        getActions().stream()
                .filter(action -> action.getName().equals(name) && !terminalState(action.getState()))
                .forEach(action -> setActionState(action, ActionState.ERROR, errorCause, errorContext));
    }

    public List<String> retryErrors() {
        List<Action> actionsToRetry = getActions().stream()
                .filter(action -> action.getState().equals(ActionState.ERROR))
                .collect(Collectors.toList());

        // this must be separate from the above stream since it mutates the original list
        actionsToRetry.forEach(action -> action.setState(ActionState.RETRIED));

        return actionsToRetry.stream().map(Action::getName).collect(Collectors.toList());
    }

    private void setActionState(Action action, ActionState actionState) {
        setActionState(action, actionState, null, null);
    }

    private void setActionState(Action action, ActionState actionState, String errorCause, String errorContext) {
        OffsetDateTime now = OffsetDateTime.now();
        action.setState(actionState);
        if (action.getCreated() == null) {
            action.setCreated(now);
        }
        action.setModified(now);
        action.setErrorCause(errorCause);
        action.setErrorContext(errorContext);
        setModified(now);
    }

    public List<String> queuedActions() {
        return getActions().stream().filter(action -> action.getState().equals(ActionState.QUEUED)).map(Action::getName).collect(Collectors.toList());
    }

    public boolean hasErrorDomain() {
        return getDomains().stream().anyMatch(d -> d.getName().equals(ERROR_DOMAIN));
    }

    public Domain getDomain(String domain) {
        return domainMap().get(domain);
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

    @SuppressWarnings("unused")
    public Enrichment getEnrichment(String enrichment) {
        return getEnrichment().stream().filter(e -> e.getName().equals(enrichment)).findFirst().orElse(null);
    }

    public void addEnrichment(@NotNull String enrichmentKey, String enrichmentValue) {
        addEnrichment(enrichmentKey, enrichmentValue, MediaType.APPLICATION_OCTET_STREAM_VALUE);
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

    public void markForDelete(String policy) {
        OffsetDateTime now = OffsetDateTime.now();

        getActions().stream()
                .filter(action -> !action.getName().equals(DeleteConstants.DELETE_ACTION))
                .filter(action -> action.getState().equals(ActionState.QUEUED))
                .forEach(action -> {
                    action.setModified(now);
                    action.setState(ActionState.ERROR);
                    action.setErrorCause("DeltaFile marked for deletion by " + policy + " policy");
                });
        queueAction(DeleteConstants.DELETE_ACTION);
        setStage(DeltaFileStage.DELETE);
        setMarkedForDelete(now);
        setMarkedForDeleteReason(policy);
        // do not update the modified time -- it breaks DeleteAction retries because the onComplete policy countdown is reset
    }

    public String sourceMetadata(String key) {
        return sourceMetadata(key, null);
    }

    public String sourceMetadata(String key, String defaultValue) {
        return getSourceInfo().getMetadata().stream().filter(k-> k.getKey().equals(key)).findFirst().map(KeyValue::getValue).orElse(defaultValue);
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
    public @NotNull List<KeyValue> getLastProtocolLayerMetadata() {
        if (Objects.isNull(getLastProtocolLayer()) || Objects.isNull(getLastProtocolLayer().getMetadata())) {
            return Collections.emptyList();
        }

        return getLastProtocolLayer().getMetadata();
    }

    @JsonIgnore
    public @NotNull Map<String, String> getLastProtocolLayerMetadataAsMap() {
        return KeyValueConverter.convertKeyValues(getLastProtocolLayerMetadata());
    }

    @Override
    public @NotNull List<Domain> getDomains() {
        return Objects.isNull(super.getDomains()) ? Collections.emptyList() : super.getDomains();
    }

    @Override
    public @NotNull List<Enrichment> getEnrichment() {
        return Objects.isNull(super.getEnrichment()) ? Collections.emptyList() : super.getEnrichment();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public DeltaFile forQueue(String actionName) {
        Builder builder = DeltaFile.newBuilder()
                .did(getDid())
                .sourceInfo(getSourceInfo())
                .domains(getDomains())
                .enrichment(getEnrichment())
                .formattedData(getFormattedData().stream()
                        .filter(f -> f.getEgressActions().contains(actionName) ||
                                (Objects.nonNull(f.getValidateActions()) && f.getValidateActions().contains(actionName)))
                        .collect(Collectors.toList()));

        if (!getProtocolStack().isEmpty()) {
            builder.protocolStack(Collections.singletonList(getLastProtocolLayer()));
        }

        return builder.build();
    }

    public static class Builder extends org.deltafi.core.domain.generated.types.DeltaFile.Builder {
        private String did;
        private List<String> parentDids;
        private List<String> childDids;
        private DeltaFileStage stage;
        private List<Action> actions;
        private SourceInfo sourceInfo;
        private List<ProtocolLayer> protocolStack;
        private List<Domain> domains;
        private List<Enrichment> enrichment;
        private List<FormattedData> formattedData;
        private OffsetDateTime created;
        private OffsetDateTime modified;

        public DeltaFile build() {
            DeltaFile result = new DeltaFile();
            result.setDid(this.did);
            result.setParentDids(this.parentDids);
            result.setChildDids(this.childDids);
            result.setStage(this.stage);
            result.setActions(this.actions);
            result.setSourceInfo(this.sourceInfo);
            result.setProtocolStack(this.protocolStack);
            result.setDomains(this.domains);
            result.setEnrichment(this.enrichment);
            result.setFormattedData(this.formattedData);
            result.setCreated(this.created);
            result.setModified(this.modified);
            return result;
        }

        public Builder did(String did) {
            this.did = did;
            return this;
        }

        public Builder parentDids(List<String> parentDids) {
            this.parentDids = parentDids;
            return this;
        }

        public Builder childDids(List<String> childDids) {
            this.childDids = childDids;
            return this;
        }

        public Builder stage(DeltaFileStage stage) {
            this.stage = stage;
            return this;
        }

        public Builder actions(List<Action> actions) {
            this.actions = actions;
            return this;
        }

        public Builder sourceInfo(SourceInfo sourceInfo) {
            this.sourceInfo = sourceInfo;
            return this;
        }

        public Builder protocolStack(List<ProtocolLayer> protocolStack) {
            this.protocolStack = protocolStack;
            return this;
        }

        public Builder domains(List<Domain> domains) {
            this.domains = domains;
            return this;
        }

        public Builder enrichment(List<Enrichment> enrichment) {
            this.enrichment = enrichment;
            return this;
        }

        public Builder formattedData(List<FormattedData> formattedData) {
            this.formattedData = formattedData;
            return this;
        }

        public Builder created(OffsetDateTime created) {
            this.created = created;
            return this;
        }

        public Builder modified(OffsetDateTime modified) {
            this.modified = modified;
            return this;
        }
    }
}
