package org.deltafi.core.action.delete;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.delete.DeleteActionBase;
import org.deltafi.actionkit.action.delete.DeleteResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.jetbrains.annotations.NotNull;

public class DeleteAction extends DeleteActionBase {
    @Override
    public Result execute(@NotNull DeltaFile deltaFile, @NotNull ActionContext context, @NotNull ActionParameters params) {
        if (!deleteContent(deltaFile.getDid())) {
            return new ErrorResult(context, "Unable to delete all objects for delta file.");
        }

        return new DeleteResult(context);
    }
}
