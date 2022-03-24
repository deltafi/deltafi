package org.deltafi.actionkit.action.transform;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.DataAmendedResult;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.ProtocolLayer;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.TransformInput;
import org.jetbrains.annotations.NotNull;

@Getter
@EqualsAndHashCode(callSuper = true)
public class TransformResult extends DataAmendedResult {
    private final String type;

    public TransformResult(@NotNull ActionContext context, @NotNull String type) {
        super(context);

        this.type = type;
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.TRANSFORM;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setTransform(TransformInput.newBuilder()
                .protocolLayer(new ProtocolLayer(type, context.getName(), content, metadata))
                .build());
        return event;
    }
}
