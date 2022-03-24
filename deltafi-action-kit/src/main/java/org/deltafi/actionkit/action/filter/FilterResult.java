package org.deltafi.actionkit.action.filter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.FilterInput;
import org.jetbrains.annotations.NotNull;

@Getter
@EqualsAndHashCode(callSuper = true)
public class FilterResult extends Result {
    private final String message;

    public FilterResult(@NotNull ActionContext context, @NotNull String message) {
        super(context);

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