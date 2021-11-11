package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.format.SimpleFormatAction;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.domain.api.types.DeltaFile;

import java.util.Arrays;
import java.util.List;

@Slf4j
public class RoteFormatAction extends SimpleFormatAction {
    public Result execute(DeltaFile deltaFile, ActionParameters params) {
        log.trace(params.getName() + " formatting (" + deltaFile.getDid() + ")");

        FormatResult result = new FormatResult(deltaFile, params, deltaFile.getSourceInfo().getFilename());

        deltaFile.getSourceInfo().getMetadata().forEach(kv -> result.addMetadata("sourceInfo." + kv.getKey(), kv.getValue()));
        params.getStaticMetadata().forEach(result::addMetadata);

        result.setObjectReference(deltaFile.getProtocolStack().get(0).getObjectReference());

        return result;
    }

    @Override
    public List<String> getRequiresDomains() {
        return Arrays.asList(DeltaFiConstants.MATCHES_ANY);
    }
}
