package org.deltafi.core.action.delete;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.metric.Metric;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventType;

import java.util.Collection;
import java.util.Collections;

public class DeleteAction extends Action<ActionParameters> {
    private final ObjectStorageService objectStorageService;

    public DeleteAction(ObjectStorageService objectStorageService) {
        super(ActionParameters.class, ActionEventType.DELETE);

        this.objectStorageService = objectStorageService;
    }

    @Override
    public Result execute(DeltaFile deltaFile, ActionContext actionContext, ActionParameters params) {
        if (!objectStorageService.removeObjects(DeltaFiConstants.MINIO_BUCKET, deltaFile.getDid())) {
            return new ErrorResult(actionContext, "Unable to remove all objects for delta file.");
        }

        return new DeleteResult(actionContext);
    }

    @Override
    public Collection<Metric> generateMetrics(Result result) {
        // The default is to include files_processed, which we don't want for the delete action.
        return Collections.emptyList();
    }
}