package org.deltafi.core.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.service.HttpService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.deltafi.core.domain.generated.types.ObjectReference;
import org.deltafi.core.parameters.RestPostEgressParameters;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class RestPostEgressAction extends EgressAction<RestPostEgressParameters> {
    private final ObjectStorageService objectStorageService;
    private final HttpService httpPostService;

    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public RestPostEgressAction(ObjectStorageService objectStorageService, HttpService httpPostService) {
        super(RestPostEgressParameters.class);

        this.objectStorageService = objectStorageService;
        this.httpPostService = httpPostService;
    }

    public Result execute(DeltaFile deltaFile, ActionContext actionContext, RestPostEgressParameters params) {
        log.debug(actionContext.getName() + " posting (" + deltaFile.getDid() + ") to: " + params.getUrl());

        // TODO: Catch exceptions from post, generate error query
        try {
            String metadataKey = params.getMetadataKey();
            String url = params.getUrl();

            // egressFeed is guaranteed to return only 1 formattedData
            FormattedData formattedData = deltaFile.getFormattedData().get(0);

            Map<String, String> headers = new HashMap<>();
            if (formattedData.getMetadata() != null) {
                formattedData.getMetadata().forEach(pair -> headers.put(pair.getKey(), pair.getValue()));
            }

            headers.put("did", deltaFile.getDid());
            headers.put("ingressFlow", deltaFile.getSourceInfo().getFlow());
            headers.put("flow", params.getEgressFlow());
            headers.put("originalFilename", deltaFile.getSourceInfo().getFilename());
            headers.put("filename", formattedData.getFilename());

            ObjectReference objectReference = formattedData.getObjectReference();
            if (objectReference.getSize() == 0) {
                // TODO: POSTing nothing is ok???
                httpPostService.post(url, Collections.singletonMap(metadataKey, objectMapper.writeValueAsString(headers)), InputStream.nullInputStream());
                log.debug("Successful egress: " + actionContext.getName() + ": " + deltaFile.getDid() + " (" + url + ")");
                return new EgressResult(actionContext, url, 0);
            }

            try (InputStream stream = objectStorageService.getObjectAsInputStream(objectReference.getBucket(),
                    objectReference.getName(), objectReference.getOffset(), objectReference.getSize())) {
                httpPostService.post(url, Collections.singletonMap(metadataKey, objectMapper.writeValueAsString(headers)), stream);
            } catch (IOException e) {
                log.error("Unable to close minio input stream", e);
            }

            log.debug("Successful egress: " + actionContext.getName() + ": " + deltaFile.getDid() + " (" + url + ")");
            return new EgressResult(actionContext, url, formattedData.getObjectReference().getSize());
        } catch (ObjectStorageException e) {
            log.error("Unable to get object from minio", e);
            return null;
        } catch (Throwable e) {
            return new ErrorResult(actionContext, "Unable to complete egress", e).logErrorTo(log);
        }
    }
}