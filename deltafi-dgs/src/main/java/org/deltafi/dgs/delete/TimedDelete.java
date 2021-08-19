package org.deltafi.dgs.delete;

import lombok.Getter;
import org.deltafi.dgs.services.DeltaFilesService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@Getter
public class TimedDelete extends DeletePolicy {
    private final Duration afterCreate;
    private final Duration afterComplete;
    private final String flow;

    public TimedDelete(DeltaFilesService deltaFilesService, String name, Map<String, String> parameters) {
        super(deltaFilesService, name, parameters);

        afterCreate = getParameters().containsKey("afterCreate") ? Duration.parse(getParameters().get("afterCreate")) : null;
        afterComplete = getParameters().containsKey("afterComplete") ? Duration.parse(getParameters().get("afterComplete")) : null;

        if ((afterCreate == null && afterComplete == null) || (afterCreate != null && afterComplete != null)) {
            throw new IllegalArgumentException("Timed delete policy " + name + " must specify exactly one afterCreate or afterComplete Durations");
        }

        flow = getParameters().get("flow");
    }

    public void run() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime createdBefore = afterCreate == null ? null : now.minus(afterCreate);
        OffsetDateTime completedBefore = afterComplete == null ? null : now.minus(afterComplete);
        deltaFilesService.markForDelete(createdBefore, completedBefore, flow, name);
    }
}
