package org.deltafi.core.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressActionParameters;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.SimpleEgressAction;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;

public class DropEgressAction extends SimpleEgressAction {
    @Override
    public Result execute(DeltaFile deltaFile, ActionContext actionContext, EgressActionParameters params) {
        return new EgressResult(actionContext, "dropped", 0);
    }
}
