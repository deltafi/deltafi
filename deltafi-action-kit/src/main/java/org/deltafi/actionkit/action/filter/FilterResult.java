package org.deltafi.actionkit.action.filter;

import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.generated.types.ActionEventInput;
import org.deltafi.dgs.generated.types.ActionEventType;
import org.deltafi.dgs.generated.types.FilterInput;

public class FilterResult extends Result {

    final String filterMessage;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public FilterResult(String name, String did, String filterMessage) {
        super(name, did);
        this.filterMessage = filterMessage;
    }

    private FilterInput filterInput() {
        return FilterInput.newBuilder()
                .message(filterMessage)
                .build();
    }

    @Override
    final public ActionEventType actionEventType() { return ActionEventType.FILTER; }

    @Override
    final public ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setFilter(filterInput());
        return event;
    }
}