package org.deltafi.actionkit.action.load;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.DataAmendedResult;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.api.types.ProtocolLayer;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.LoadInput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = true)
public class LoadResult extends DataAmendedResult {
    private final List<KeyValue> domains = new ArrayList<>();

    public LoadResult(ActionContext actionContext, DeltaFile deltaFile) {
        super(actionContext);
        setContentReference(deltaFile.getProtocolStack().get(deltaFile.getProtocolStack().size() - 1).getContentReference());
    }

    public void addDomain(@NotNull String domainName, String value) {
        domains.add(new KeyValue(domainName, value));
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
                .protocolLayer(new ProtocolLayer(actionContext.getName(), actionContext.getName(), contentReference, metadata))
                .build());

        return event;
    }
}
