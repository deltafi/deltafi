package org.deltafi.core.action.delete;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;

public class DeleteAction extends Action<ActionParameters> {
    private final ContentStorageService contentStorageService;

    public DeleteAction(ContentStorageService contentStorageService) {
        super(ActionParameters.class);

        this.contentStorageService = contentStorageService;
    }

    @Override
    public Result execute(DeltaFile deltaFile, ActionContext actionContext, ActionParameters params) {
        if (!contentStorageService.deleteAll(deltaFile.getDid())) {
            return new ErrorResult(actionContext, "Unable to remove all objects for delta file.");
        }

        return new DeleteResult(actionContext);
    }
}
