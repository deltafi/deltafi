package org.deltafi.actionkit.action.load;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.DataAmendedResult;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.ProtocolLayer;
import org.deltafi.core.domain.generated.types.*;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@EqualsAndHashCode(callSuper = true)
public class LoadResult extends DataAmendedResult {
    private final List<DomainInput> domains = new ArrayList<>();

    @SuppressWarnings("CdiInjectionPointsInspection")
    public LoadResult(ActionContext actionContext, DeltaFile deltaFile) {
        super(actionContext);
        setContentReference(deltaFile.getProtocolStack().get(deltaFile.getProtocolStack().size() - 1).getContentReference());
    }

    public void addDomain(@NotNull String domainName) {
        addDomain(domainName, null, null);
    }

    public void addDomain(@NotNull String domainName, String value) {
        addDomain(domainName, value, null);
    }

    public void addDomain(@NotNull String domainName, String value, String mediaType) {
        domains.add(new DomainInput(domainName, value, Objects.isNull(mediaType) ? MediaType.APPLICATION_OCTET_STREAM : mediaType));
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
