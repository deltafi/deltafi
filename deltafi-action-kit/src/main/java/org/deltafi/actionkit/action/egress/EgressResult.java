package org.deltafi.actionkit.action.egress;

import lombok.Getter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventType;

@Getter
public class EgressResult<P extends EgressActionParameters> extends Result<P> {
    private final String destination;
    private final long bytesEgressed;

    public EgressResult(DeltaFile deltaFile, P params, String destination, long bytesEgressed) {
        super(deltaFile, params);

        this.destination = destination;
        this.bytesEgressed = bytesEgressed;
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.EGRESS;
    }
}