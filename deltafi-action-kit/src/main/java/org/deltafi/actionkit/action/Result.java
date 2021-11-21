package org.deltafi.actionkit.action;

import lombok.Data;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.*;

import java.time.OffsetDateTime;

@Data
public abstract class Result {
    protected final DeltaFile deltaFile;
    protected final ActionParameters params;

    public abstract ActionEventType actionEventType();

    public ActionEventInput toEvent() {
        return ActionEventInput.newBuilder()
                .did(deltaFile.getDid())
                .action(params.getName())
                .time(OffsetDateTime.now())
                .type(actionEventType())
                .build();
    }
}
