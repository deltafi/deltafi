package org.deltafi.core.action;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.minio.ObjectWriteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.format.FormatAction;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.ErrorDomain;
import org.deltafi.core.domain.generated.types.ObjectReference;

import java.util.Objects;

import static org.deltafi.common.constant.DeltaFiConstants.MINIO_BUCKET;

@RequiredArgsConstructor
@Slf4j
public class SimpleErrorFormatAction extends FormatAction<ActionParameters> {
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.DEFAULT_VIEW_INCLUSION, true)
            .registerModule(new JavaTimeModule());

    private final ObjectStorageService objectStorageService;

    @Override
    public Result execute(DeltaFile deltaFile, ActionParameters params) {
        log.warn(params.getName() + " formatting (" + deltaFile.getDid() + ")");

        if (Objects.isNull(deltaFile.getDomain("error"))) {
            log.error("Error domain missing with did: {}", deltaFile.getDid());
            throw new RuntimeException("Error domain missing: " + deltaFile.getDid());
        }
        String json = deltaFile.getDomain("error");

        ErrorDomain errorDomain;
        try {
            errorDomain = OBJECT_MAPPER.readValue(json, ErrorDomain.class);
        } catch (Exception e) {
            throw new RuntimeException("Error converting JSON to ErrorDomain.\n" + json, e);
        }

        String filename;
        try {
            filename = errorDomain.getOriginatorDid() +
                    "." + deltaFile.getSourceInfo().getFilename() +
                    ".error";
        } catch (Throwable t) {
            String err = "DeltaFile: Could not determine source file name for " + deltaFile.getDid();
            log.error(err, t);
            throw new RuntimeException(err, t);
        }

        FormatResult result = new FormatResult(params.getName(), deltaFile.getDid(), filename);
        addSourceInputMetadata(result, deltaFile);
        addProtocolStackMetadata(result, deltaFile);

        try {
            ObjectWriteResponse objectWriteResponse = objectStorageService.putObject(
                    MINIO_BUCKET, deltaFile.getDid() + "/" + params.getName(), json.getBytes());
            result.setObjectReference(fromObjectWriteResponse(objectWriteResponse, json.getBytes().length));
        } catch (ObjectStorageException e) {
            throw new RuntimeException("Failed to write transformed data to minio " + e.getMessage());
        }

        logFilesProcessedMetric(ActionEventType.FORMAT, deltaFile);

        return result;
    }

    private ObjectReference fromObjectWriteResponse(ObjectWriteResponse response, long size) {
        return ObjectReference.newBuilder()
                .bucket(response.bucket())
                .name(response.object())
                .size(size)
                .build();
    }

    @Override
    public Class<ActionParameters> getParamType() {
        return ActionParameters.class;
    }
}