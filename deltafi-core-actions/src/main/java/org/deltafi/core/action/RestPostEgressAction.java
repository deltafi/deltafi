package org.deltafi.core.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.service.HttpService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.deltafi.core.parameters.RestPostEgressParameters;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class RestPostEgressAction extends EgressAction<RestPostEgressParameters> {
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Inject
    HttpService httpPostService;

    public RestPostEgressAction() {
        super(RestPostEgressParameters.class);
    }

    public Result execute(DeltaFile deltaFile, ActionContext actionContext, RestPostEgressParameters params) {
        log.debug(actionContext.getName() + " posting (" + deltaFile.getDid() + ") to: " + params.getUrl());

        FormattedData formattedData = getFormattedData(deltaFile);

        try (InputStream inputStream = contentStorageService.load(formattedData.getContentReference())) {
            HttpResponse<InputStream> response = httpPostService.post(params.getUrl(), Map.of(params.getMetadataKey(),
                    buildHeadersMapString(deltaFile, params)), inputStream, formattedData.getContentReference().getMediaType());
            Response.Status status = Response.Status.fromStatusCode(response.statusCode());
            if (Objects.isNull(status) || status.getFamily() != Response.Status.Family.SUCCESSFUL) {
                return new ErrorResult(actionContext, "Unsuccessful POST: " + response.statusCode() + " " + new String(response.body().readAllBytes())).logErrorTo(log);
            }

        } catch (JsonProcessingException e) {
            return new ErrorResult(actionContext, "Unable to build post headers", e).logErrorTo(log);
        } catch (ObjectStorageException e) {
            return new ErrorResult(actionContext, "Unable to get object from content storage", e).logErrorTo(log);
        } catch (IOException e) {
            log.warn("Unable to close input stream from content storage", e);
        }

        log.debug("Successful egress: " + actionContext.getName() + ": " + deltaFile.getDid() + " (" + params.getUrl() + ")");
        return new EgressResult(actionContext, params.getUrl(), formattedData.getContentReference().getSize());
    }

    private FormattedData getFormattedData(DeltaFile deltaFile) {
        // egressFeed is guaranteed to return only 1 formattedData
        return deltaFile.getFormattedData().get(0);
    }

    private String buildHeadersMapString(DeltaFile deltaFile, RestPostEgressParameters params)
            throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(buildHeadersMap(deltaFile, params));
    }

    private Map<String, String> buildHeadersMap(DeltaFile deltaFile, RestPostEgressParameters params) {
        FormattedData formattedData = getFormattedData(deltaFile);

        Map<String, String> headersMap = new HashMap<>();
        if (formattedData.getMetadata() != null) {
            formattedData.getMetadata().forEach(pair -> headersMap.put(pair.getKey(), pair.getValue()));
        }
        headersMap.put("did", deltaFile.getDid());
        headersMap.put("ingressFlow", deltaFile.getSourceInfo().getFlow());
        headersMap.put("flow", params.getEgressFlow());
        headersMap.put("originalFilename", deltaFile.getSourceInfo().getFilename());
        headersMap.put("filename", formattedData.getFilename());

        return headersMap;
    }
}