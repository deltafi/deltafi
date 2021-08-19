package org.deltafi.commonactions.action.delete;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.SimpleAction;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.service.ContentService;
import org.deltafi.dgs.api.types.DeltaFile;

import javax.inject.Inject;

public class DeleteAction extends SimpleAction {
    @Inject
    private ContentService contentService;

    @Override
    public Result execute(DeltaFile deltaFile, ActionParameters params) {
        if (!contentService.deleteObjectsForDeltaFile(deltaFile)) {
            return new ErrorResult(getClass().getCanonicalName(), deltaFile,
                    "Unable to delete all objects for delta file.");
        }

        return new DeleteResult(params.getName(), deltaFile.getDid());
    }
}
