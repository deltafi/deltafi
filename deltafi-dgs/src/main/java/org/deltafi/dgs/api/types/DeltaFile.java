package org.deltafi.dgs.api.types;

import org.deltafi.dgs.generated.types.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Document
@CompoundIndex(name = "dispatch_index", def = "{'actions.name': 1, 'actions.state': 1, 'actions.modified': 1}")
@CompoundIndex(name = "completed_before_index", def="{'stage': 1, 'modified': 1, 'sourceInfo.flow': 1}")
@CompoundIndex(name = "created_before_index", def="{'created': 1, 'sourceInfo.flow': 1}")
public class DeltaFile extends org.deltafi.dgs.generated.types.DeltaFile {

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
        Action action = new Action();
        action.setName(name);
        setActionState(action, ActionState.QUEUED);
        getActions().add(action);
    }

    public Optional<Action> actionNamed(String name) {
        return getActions().stream()
                .filter(a -> a.getName().equals(name))
                .findFirst();
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
                .filter(action -> action.getName().equals(name))
                .forEach(action -> setActionState(action, ActionState.COMPLETE));
    }

    public void filterAction(String name, String filterMessage) {
        getActions().stream()
                .filter(action -> action.getName().equals(name))
                .forEach(action -> setActionState(action, ActionState.FILTERED, filterMessage));
    }

    public void errorAction(String name, String errorMessage) {
        getActions().stream()
                .filter(action -> action.getName().equals(name))
                .forEach(action -> setActionState(action, ActionState.ERROR, errorMessage));
    }

    public void retryErrors() {
        getActions().stream()
                .filter(action -> action.getState().equals(ActionState.ERROR))
                .forEach(action -> setActionState(action, ActionState.QUEUED));
    }

    private void setActionState(Action action, ActionState actionState) {
        setActionState(action, actionState, null);
    }

    private void setActionState(Action action, ActionState actionState, String errorMessage) {
        OffsetDateTime now = OffsetDateTime.now();
        action.setState(actionState);
        if (action.getCreated() == null) {
            action.setCreated(now);
        }
        action.setModified(now);
        action.setErrorMessage(errorMessage);
        setModified(now);
    }

    public List<String> queuedActions() {
        return getActions().stream().filter(action -> action.getState().equals(ActionState.QUEUED)).map(Action::getName).collect(Collectors.toList());
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

    public boolean hasTerminalAction(String name) {
        return getActions().stream().anyMatch(action -> action.getName().equals(name) && terminalState(action.getState()));
    }

    public boolean hasCompletedAction(String name) {
        return getActions().stream().anyMatch(action -> action.getName().equals(name) && action.getState().equals(ActionState.COMPLETE));
    }

    public boolean hasCompletedActions(List<String> names) {
        return names.stream().allMatch(this::hasCompletedAction);
    }

    public void markForDelete(String deleteAction, String policy) {
        OffsetDateTime now = OffsetDateTime.now();

        getActions().stream()
                .filter(action -> !action.getName().equals(deleteAction))
                .filter(action -> action.getState().equals(ActionState.QUEUED))
                .forEach(a -> {
            a.setModified(now);
            a.setState(ActionState.ERROR);
            a.setErrorMessage("DeltaFile marked for deletion by " + policy + " policy");
        });
        queueAction(deleteAction);
        setStage(DeltaFileStage.DELETE.name());
        setModified(now);
    }


    public static Builder newBuilder() {
        return new Builder();
    }

    public DeltaFile forQueue(String actionName) {
        Builder builder = DeltaFile.newBuilder()
                .did(getDid())
                .sourceInfo(getSourceInfo())
                .protocolStack(Collections.singletonList(getProtocolStack().get(getProtocolStack().size()-1)));

        if (getStage().equals(DeltaFileStage.ENRICH.name()) || getStage().equals(DeltaFileStage.FORMAT.name())) {
            // TODO: trim down domains and enrichment to those that the current action cares about
            builder.domains(getDomains())
                    .enrichment(getEnrichment());
        } else if (getStage().equals(DeltaFileStage.VALIDATE.name()) || getStage().equals(DeltaFileStage.EGRESS.name())) {
            builder.formattedData(getFormattedData().stream()
                    .filter(f -> f.getEgressActions().contains(actionName))
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    public static class Builder extends org.deltafi.dgs.generated.types.DeltaFile.Builder{
        private String did;
        private String stage;
        private List<Action> actions;
        private SourceInfo sourceInfo;
        private List<ProtocolLayer> protocolStack;
        private DeltaFiDomains domains;
        private DeltaFiEnrichments enrichment;
        private List<FormattedData> formattedData;
        private OffsetDateTime created;
        private OffsetDateTime modified;

        public DeltaFile build() {
            DeltaFile result = new DeltaFile();
            result.setDid(this.did);
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

        public Builder stage(String stage) {
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

        public Builder domains(DeltaFiDomains domains) {
            this.domains = domains;
            return this;
        }

        public Builder enrichment(DeltaFiEnrichments enrichment) {
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
