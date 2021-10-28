package org.deltafi.actionkit.action.format;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;

@Slf4j
public abstract class FormatAction<P extends ActionParameters> extends Action<P> {
    protected void addSourceInputMetadata(FormatResult result, DeltaFile deltaFile) {
        if (deltaFile.getSourceInfo() != null && deltaFile.getSourceInfo().getMetadata() != null) {
            deltaFile.getSourceInfo().getMetadata().forEach(kv -> result.addMetadata("sourceInfo." + kv.getKey(), kv.getValue()));
        }
    }

    protected void addProtocolStackMetadata(FormatResult result, DeltaFile deltaFile) {
        if (deltaFile.getProtocolStack() != null) {
            deltaFile.getProtocolStack().forEach(ps -> result.addMetadata(ps.getMetadata()));
        }
    }
}