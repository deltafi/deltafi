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
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.deltafi.core.parameters.RestPostEgressParameters;
import org.jetbrains.annotations.NotNull;

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

    @SuppressWarnings("BusyWait")
    public Result egress(@NotNull ActionContext context, @NotNull RestPostEgressParameters params, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData) {
        int tries = 0;

        while (true) {
            Result result = doEgress(context, params, sourceInfo, formattedData);
            tries++;

            if (result instanceof ErrorResult) {
                if (tries > params.getRetryCount()) {
                    return result;
                } else {
                    log.error("Retrying POST after error: " + ((ErrorResult) result).getErrorCause());
                    try {
                        Thread.sleep(params.getRetryDelayMs());
                    } catch (InterruptedException ignored) {}
                }
            } else {
                return result;
            }
        }
    }

    private Result doEgress(@NotNull ActionContext context, @NotNull RestPostEgressParameters params, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData) {
        try (InputStream inputStream = loadContentAsInputStream(formattedData.getContentReference())) {
            HttpResponse<InputStream> response = httpPostService.post(params.getUrl(), Map.of(params.getMetadataKey(),
                    buildHeadersMapString(context.getDid(), sourceInfo, formattedData, params)), inputStream, formattedData.getContentReference().getMediaType());
            Response.Status status = Response.Status.fromStatusCode(response.statusCode());
            if (Objects.isNull(status) || status.getFamily() != Response.Status.Family.SUCCESSFUL) {
                return new ErrorResult(context, "Unsuccessful POST: " + response.statusCode() + " " + new String(response.body().readAllBytes())).logErrorTo(log);
            }

        } catch (JsonProcessingException e) {
            return new ErrorResult(context, "Unable to build post headers", e);
        } catch (ObjectStorageException e) {
            return new ErrorResult(context, "Unable to get object from content storage", e);
        } catch (IOException e) {
            log.warn("Unable to close input stream from content storage", e);
        }

        return new EgressResult(context, params.getUrl(), formattedData.getContentReference().getSize());
    }

    private String buildHeadersMapString(String did, SourceInfo sourceInfo, FormattedData formattedData, RestPostEgressParameters params)
            throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(buildHeadersMap(did, sourceInfo, formattedData, params));
    }

    private Map<String, String> buildHeadersMap(String did, SourceInfo sourceInfo, FormattedData formattedData, RestPostEgressParameters params) {
        Map<String, String> headersMap = new HashMap<>();
        if (formattedData.getMetadata() != null) {
            formattedData.getMetadata().forEach(pair -> headersMap.put(pair.getKey(), pair.getValue()));
        }
        headersMap.put("did", did);
        headersMap.put("ingressFlow", sourceInfo.getFlow());
        headersMap.put("flow", params.getEgressFlow());
        headersMap.put("originalFilename", sourceInfo.getFilename());
        headersMap.put("filename", formattedData.getFilename());

        return headersMap;
    }
}