package org.deltafi.actionkit.action;

import org.deltafi.dgs.generated.types.ActionEventInput;
import org.deltafi.dgs.generated.types.ActionEventType;

import java.time.OffsetDateTime;

abstract public class Result {
    protected final String name;
    protected final String did;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public Result(String name, String did) {
        this.name = name;
        this.did = did;
    }

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