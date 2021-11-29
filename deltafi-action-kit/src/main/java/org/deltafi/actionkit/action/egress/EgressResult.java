package org.deltafi.actionkit.action.egress;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventType;

@Getter
@EqualsAndHashCode(callSuper = true)
public class EgressResult extends Result {
    private final String destination;
    private final long bytesEgressed;

    public EgressResult(DeltaFile deltaFile, ActionParameters params, String destination, long bytesEgressed) {
        super(deltaFile, params);

        this.destination = destination;
        this.bytesEgressed = bytesEgressed;
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.EGRESS;
    }
}