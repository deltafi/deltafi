package org.deltafi.actionkit.action.load;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.DataAmendedResult;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.ProtocolLayer;
import org.deltafi.core.domain.generated.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = true)
public class LoadResult extends DataAmendedResult {
    private final List<DomainInput> domains = new ArrayList<>();

    public LoadResult(ActionContext actionContext, DeltaFile deltaFile) {
        super(actionContext);
        setContentReference(deltaFile.getLastContentReference());
    }

    public void addDomain(@NotNull String domainName, String value, String mediaType) {
        domains.add(new DomainInput(domainName, value, mediaType));
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
