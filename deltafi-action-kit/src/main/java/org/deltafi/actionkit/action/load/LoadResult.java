package org.deltafi.actionkit.action.load;

import lombok.EqualsAndHashCode;
import org.deltafi.actionkit.action.DataAmendedResult;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
public class LoadResult extends DataAmendedResult {
    private final List<KeyValueInput> domains = new ArrayList<>();

    public LoadResult(ActionContext actionContext, DeltaFile deltaFile) {
        super(actionContext);
        setObjectReference(deltaFile.getProtocolStack().get(deltaFile.getProtocolStack().size() -1).getObjectReference());
    }

    public void addDomain(@NotNull String domainName, String value) {
        domains.add(KeyValueInput.newBuilder().key(domainName).value(value).build());
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
                                .type(actionContext.getName())
                                .objectReference(objectReferenceInput)
                                .metadata(metadata)
                                .build())
                .build());

        return event;
    }
}
