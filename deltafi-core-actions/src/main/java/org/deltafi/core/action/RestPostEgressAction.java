package org.deltafi.core.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.service.HttpService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.deltafi.core.domain.generated.types.ObjectReference;
import org.deltafi.core.parameters.RestPostEgressParameters;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class RestPostEgressAction extends EgressAction<RestPostEgressParameters> {
    private final ObjectStorageService objectStorageService;
    private final HttpService httpPostService;

    public RestPostEgressAction(ObjectStorageService objectStorageService, HttpService httpPostService) {
        super(RestPostEgressParameters.class);

        this.objectStorageService = objectStorageService;
        this.httpPostService = httpPostService;
    }

    public Result<RestPostEgressParameters> execute(DeltaFile deltaFile, RestPostEgressParameters params) {
        log.debug(params.getName() + " posting (" + deltaFile.getDid() + ") to: " + params.getUrl());

        // TODO: Catch exceptions from post, generate error query
        try {
            String prefix = params.getMetadataPrefix();
            String url = params.getUrl();

            // egressFeed is guaranteed to return only 1 formattedData
            FormattedData formattedData = deltaFile.getFormattedData().get(0);

            Map<String, String> headers = new HashMap<>();
            if (formattedData.getMetadata() != null) {
                formattedData.getMetadata().forEach(pair -> headers.put(prefix + pair.getKey(), pair.getValue()));
            }
            params.getStaticMetadata().forEach((k, v) -> headers.put(prefix + k, v));
            headers.put(prefix + "did", deltaFile.getDid());
            headers.put(prefix + "ingressFlow", deltaFile.getSourceInfo().getFlow());
            headers.put(prefix + "flow", params.getEgressFlow());
            headers.put(prefix + "originalFilename", deltaFile.getSourceInfo().getFilename());
            headers.put(prefix + "filename", formattedData.getFilename());

            ObjectReference objectReference = formattedData.getObjectReference();
            if (objectReference.getSize() == 0) {
                // TODO: POSTing nothing is ok???
                httpPostService.post(url, headers, InputStream.nullInputStream());
                log.debug("Successful egress: " + params.getName() + ": " + deltaFile.getDid() + " (" + url + ")");
                return new EgressResult<>(deltaFile, params, url, 0);
            }

            try (InputStream stream = objectStorageService.getObjectAsInputStream(objectReference.getBucket(),
                    objectReference.getName(), objectReference.getOffset(), objectReference.getSize())) {
                httpPostService.post(url, headers, stream);
            } catch (IOException e) {
                log.error("Unable to close minio input stream", e);
            }

            log.debug("Successful egress: " + params.getName() + ": " + deltaFile.getDid() + " (" + url + ")");
            return new EgressResult<>(deltaFile, params, url, formattedData.getObjectReference().getSize());
        } catch (ObjectStorageException e) {
            log.error("Unable to get object from minio", e);
            return null;
        } catch (Throwable e) {
            return new ErrorResult<>(deltaFile, params, "Unable to complete egress", e).logErrorTo(log);
        }
    }
}