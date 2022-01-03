package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.format.SimpleFormatAction;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;

import java.util.List;

@Slf4j
public class RoteFormatAction extends SimpleFormatAction {
    public Result execute(DeltaFile deltaFile, ActionContext actionContext, ActionParameters params) {
        log.trace(actionContext.getName() + " formatting (" + deltaFile.getDid() + ")");

        FormatResult result = new FormatResult(actionContext, deltaFile.getSourceInfo().getFilename());
        result.setContentReference(deltaFile.getProtocolStack().get(0).getContentReference());
        addSourceInputMetadata(result, deltaFile);
        addProtocolStackMetadata(result, deltaFile);
        return result;
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }
}
