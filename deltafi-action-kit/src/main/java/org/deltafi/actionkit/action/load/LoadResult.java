package org.deltafi.actionkit.action.load;

import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.LoadInput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class LoadResult extends Result {

    @SuppressWarnings("CdiInjectionPointsInspection")
    public LoadResult(String name, String did) {
        super(name, did);
    }

    protected final List<String> domains = new ArrayList<>();

    private LoadInput loadInput() {
        return LoadInput.newBuilder()
                .domains(domains)
                .build();
    }

    public void addDomain(@NotNull String domain) {
        domains.add(domain);
    }

    @Override
    final public ActionEventType actionEventType() { return ActionEventType.LOAD; }

    @Override
    final public ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setLoad(loadInput());
        return event;
    }
}