package org.deltafi.core.action;

import lombok.extern.slf4j.Slf4j;
import org.apache.nifi.util.FlowFilePackagerV1;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.service.HttpService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.deltafi.core.parameters.HttpEgressParameters;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.io.*;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class FlowfileEgressAction extends HttpEgressActionBase<HttpEgressParameters> {
    @Inject
    HttpService httpPostService;

    private static final String FLOWFILE_V1_CONTENT_TYPE = "application/flowfile-v1";

    public FlowfileEgressAction() {
        super(HttpEgressParameters.class);
    }

    protected Result doEgress(@NotNull ActionContext context, @NotNull HttpEgressParameters params, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData) {
        try (InputStream inputStream = loadContentAsInputStream(formattedData.getContentReference())) {
            FlowFilePackagerV1 packager = new FlowFilePackagerV1();
            try(ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                packager.packageFlowFile(inputStream, out,
                        buildHeadersMap(context.getDid(), sourceInfo, formattedData, params),
                        formattedData.getContentReference().getSize());
                HttpResponse<InputStream> response;
                try (ByteArrayInputStream flowfile = new ByteArrayInputStream(out.toByteArray())) {
                    response = httpPostService.post(params.getUrl(), Map.of(), flowfile, FLOWFILE_V1_CONTENT_TYPE);
                } catch (IOException e) {
                    return new ErrorResult(context, "Unable to process flowfile stream", e);
                }
                Response.Status status = Response.Status.fromStatusCode(response.statusCode());
                if (Objects.isNull(status) || status.getFamily() != Response.Status.Family.SUCCESSFUL) {
                    return new ErrorResult(context, "Unsuccessful HTTP POST: " + response.statusCode() + " " + new String(response.body().readAllBytes())).logErrorTo(log);
                }
            } catch (IOException e) {
                return new ErrorResult(context, "Unable to extract flowfile content");
            }
        } catch (ObjectStorageException e) {
            return new ErrorResult(context, "Unable to get object from content storage", e);
        } catch (IOException e) {
            log.warn("Unable to close input stream from content storage", e);
        }

        return new EgressResult(context, params.getUrl(), formattedData.getContentReference().getSize());
    }

}