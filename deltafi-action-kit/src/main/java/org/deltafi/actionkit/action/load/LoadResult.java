package org.deltafi.actionkit.action.load;

import org.deltafi.actionkit.action.DataAmendedResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.LoadInput;
import org.deltafi.core.domain.generated.types.ProtocolLayerInput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class LoadResult extends DataAmendedResult {
    private final List<String> domains = new ArrayList<>();

    public LoadResult(DeltaFile deltaFile, ActionParameters params) {
        super(deltaFile, params);
        setObjectReference(deltaFile.getProtocolStack().get(deltaFile.getProtocolStack().size() -1).getObjectReference());
    }

    public void addDomain(@NotNull String domain) {
        domains.add(domain);
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.LOAD;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setLoad(LoadInput.newBuilder()
                .domains(domains)
                .protocolLayer(
                        new ProtocolLayerInput.Builder()
                                .type(params.getName())
                                .objectReference(objectReferenceInput)
                                .metadata(metadata)
                                .build())
                .build());

        return event;
    }
}
