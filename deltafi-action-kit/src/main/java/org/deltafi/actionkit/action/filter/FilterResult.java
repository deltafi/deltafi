package org.deltafi.actionkit.action.filter;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.FilterInput;

public class FilterResult extends Result<ActionParameters> {
    private final String message;

    public FilterResult(DeltaFile deltaFile, ActionParameters params, String message) {
        super(deltaFile, params);

        this.message = message;
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.FILTER;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setFilter(FilterInput.newBuilder().message(message).build());
        return event;
    }
}