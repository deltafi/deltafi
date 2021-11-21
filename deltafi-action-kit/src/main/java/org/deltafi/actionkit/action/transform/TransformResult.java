package org.deltafi.actionkit.action.transform;

import org.deltafi.actionkit.action.DataAmendedResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.ProtocolLayerInput;
import org.deltafi.core.domain.generated.types.TransformInput;

public class TransformResult extends DataAmendedResult {
    private final String type;

    public TransformResult(DeltaFile deltaFile, ActionParameters params, String type) {
        super(deltaFile, params);

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
                .protocolLayer(
                        new ProtocolLayerInput.Builder()
                                .type(type)
                                .objectReference(objectReferenceInput)
                                .metadata(metadata)
                                .build())
                .build());
        return event;
    }
}
