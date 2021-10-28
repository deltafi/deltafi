package org.deltafi.core.action;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.service.HttpService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.deltafi.core.domain.generated.types.ObjectReference;
import org.deltafi.core.parameters.RestPostEgressParameters;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class RestPostEgressAction extends EgressAction<RestPostEgressParameters> {
    private final ObjectStorageService objectStorageService;
    private final HttpService httpPostService;

    public Result execute(DeltaFile deltafile, RestPostEgressParameters params) {
        log.debug(params.getName() + " posting (" + deltafile.getDid() + ") to: " + params.getUrl());

        // TODO: Catch exceptions from post, generate error query
        try {
            String prefix = params.getMetadataPrefix();
            String url = params.getUrl();

            // egressFeed is guaranteed to return only 1 formattedData
            FormattedData formattedData = deltafile.getFormattedData().get(0);

            Map<String, String> headers = new HashMap<>();
            if (formattedData.getMetadata() != null) {
                formattedData.getMetadata().forEach(pair -> headers.put(prefix + pair.getKey(), pair.getValue()));
            }
            params.getStaticMetadata().forEach((k, v) -> headers.put(prefix + k, v));
            headers.put(prefix + "did", deltafile.getDid());
            headers.put(prefix + "ingressFlow", deltafile.getSourceInfo().getFlow());
            headers.put(prefix + "flow", params.getEgressFlow());
            headers.put(prefix + "originalFilename", deltafile.getSourceInfo().getFilename());
            headers.put(prefix + "filename", formattedData.getFilename());

            ObjectReference objectReference = formattedData.getObjectReference();
            if (objectReference.getSize() == 0) {
                // TODO: POSTing nothing is ok???
                httpPostService.post(url, headers, InputStream.nullInputStream());

                logEgressMetrics(deltafile, 0, url);
            } else {
                try (InputStream stream = objectStorageService.getObjectAsInputStream(objectReference.getBucket(),
                        objectReference.getName(), objectReference.getOffset(), objectReference.getSize())) {
                    httpPostService.post(url, headers, stream);

                    logEgressMetrics(deltafile, formattedData.getObjectReference().getSize(), url);
                } catch (IOException e) {
                    log.error("Unable to close minio input stream", e);
                }
            }
            log.info("Successful egress: " + params.getName() + ": " + deltafile.getDid() + " (" + url + ")");
            return new EgressResult(params.getName(), deltafile.getDid());
        } catch (ObjectStorageException e) {
            log.error("Unable to get object from minio", e);
            return null;
        } catch (Throwable e) {
            return new ErrorResult(params.getName(), deltafile, "Unable to complete egress", e).logErrorTo(log);
        }
    }

    private void logEgressMetrics(DeltaFile deltaFile, long size, String endpoint) {
        logMetric(ActionEventType.EGRESS, deltaFile, "files_out", 1);
        logMetric(ActionEventType.EGRESS, deltaFile, "bytes_out", size, Map.of("endpoint", endpoint));
    }

    @Override
    public Class<RestPostEgressParameters> getParamType() {
        return RestPostEgressParameters.class;
    }
}