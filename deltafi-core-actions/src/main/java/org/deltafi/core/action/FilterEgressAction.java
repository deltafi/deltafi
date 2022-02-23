package org.deltafi.core.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressActionParameters;
import org.deltafi.actionkit.action.egress.SimpleEgressAction;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;

public class FilterEgressAction extends SimpleEgressAction {
    @Override
    public Result execute(DeltaFile deltaFile, ActionContext actionContext, EgressActionParameters params) {
        return new FilterResult(actionContext, "filtered");
    }
}
