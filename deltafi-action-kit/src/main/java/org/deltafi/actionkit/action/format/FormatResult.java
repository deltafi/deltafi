package org.deltafi.actionkit.action.format;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.FormatInput;

public class FormatResult extends Result<ActionParameters> {
    private final String filename;

    public FormatResult(DeltaFile deltaFile, ActionParameters params, String filename) {
        super(deltaFile, params);

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
                .objectReference(objectReferenceInput)
                .metadata(metadata)
                .build());
        return event;
    }
}