package org.deltafi.core.action.delete;

import lombok.RequiredArgsConstructor;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.SimpleAction;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.core.domain.api.types.DeltaFile;

import javax.inject.Inject;

import static org.deltafi.common.constant.DeltaFiConstants.MINIO_BUCKET;

@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class DeleteAction extends SimpleAction {
    private final ObjectStorageService objectStorageService;

    @Override
    public Result execute(DeltaFile deltaFile, ActionParameters params) {
        if (!objectStorageService.removeObjects(MINIO_BUCKET, deltaFile.getDid())) {
            return new ErrorResult(getClass().getCanonicalName(), deltaFile,
                    "Unable to remove all objects for delta file.");
        }

        return new DeleteResult(params.getName(), deltaFile.getDid());
    }
}