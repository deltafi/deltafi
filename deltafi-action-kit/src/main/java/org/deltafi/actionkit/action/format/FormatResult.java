package org.deltafi.actionkit.action.format;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.DataAmendedResult;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.FormatInput;

@Getter
@EqualsAndHashCode(callSuper = true)
public class FormatResult extends DataAmendedResult {
    private final String filename;

    public FormatResult(ActionContext actionContext, String filename) {
        super(actionContext);
        this.filename = filename;
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.FORMAT;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setFormat(FormatInput.newBuilder()
                .filename(filename)
                .contentReference(contentReference)
                .metadata(metadata)
                .build());
        return event;
    }
}
