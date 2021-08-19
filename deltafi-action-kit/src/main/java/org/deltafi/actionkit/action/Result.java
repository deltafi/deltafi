package org.deltafi.actionkit.action;

import lombok.RequiredArgsConstructor;
import org.deltafi.dgs.generated.types.ActionEventInput;
import org.deltafi.dgs.generated.types.ActionEventType;

import java.time.OffsetDateTime;

@RequiredArgsConstructor
abstract public class Result {
    protected final String name;
    protected final String did;

    abstract public ActionEventType actionEventType();

    public ActionEventInput toEvent() {
        return ActionEventInput.newBuilder()
                .did(did)
                .action(name)
                .time(OffsetDateTime.now())
                .type(actionEventType())
                .build();
    }
}