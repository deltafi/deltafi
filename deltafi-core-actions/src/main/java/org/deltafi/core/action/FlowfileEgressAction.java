/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.action;

import lombok.extern.slf4j.Slf4j;
import org.apache.nifi.util.FlowFilePackagerV1;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.exception.HttpPostException;
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
    static final String FLOWFILE_V1_CONTENT_TYPE = "application/flowfile";
    @Inject
    HttpService httpPostService;

    public FlowfileEgressAction() {
        super(HttpEgressParameters.class);
    }

    protected Result doEgress(@NotNull ActionContext context, @NotNull HttpEgressParameters params, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData) {
        try (InputStream inputStream = loadContentAsInputStream(formattedData.getContentReference())) {
            FlowFilePackagerV1 packager = new FlowFilePackagerV1();
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                packager.packageFlowFile(inputStream, out,
                        buildHeadersMap(context.getDid(), sourceInfo, formattedData, params),
                        formattedData.getContentReference().getSize());
                HttpResponse<InputStream> response;

                // In order to avoid multiple copies of the stream in memory to convert from an output stream to an input
                // stream, the Piped*Stream pattern is used.
                PipedOutputStream pipedOutput = new PipedOutputStream();
                try (PipedInputStream pipedInput = new PipedInputStream(pipedOutput)) {
                    Thread pump = new Thread(() -> {
                        try {
                            out.writeTo(pipedOutput);
                            pipedOutput.close();
                        } catch (Throwable e) {
                            // An unclosed pipe will result in an exception on the other side, which will
                            // be responsible for propagating the appropriate error response
                            log.error("Unable to push flowfile through pipe", e);
                        }
                    });
                    pump.start();
                    try {
                        response = httpPostService.post(params.getUrl(), Map.of(), pipedInput, FLOWFILE_V1_CONTENT_TYPE);
                    } catch (Throwable e) {
                        return new ErrorResult(context, "Unable to process flowfile stream", e);
                    }
                    pump.join();
                }
                Response.Status status = Response.Status.fromStatusCode(response.statusCode());
                if (Objects.isNull(status) || status.getFamily() != Response.Status.Family.SUCCESSFUL) {
                    String body;
                    try (InputStream is = response.body()) {
                        body = new String(is.readAllBytes());
                    }
                    return new ErrorResult(context, "Unsuccessful HTTP POST: " + response.statusCode() + " " + body).logErrorTo(log);
                }
            } catch (IOException e) {
                return new ErrorResult(context, "Unable to extract flowfile content");
            } catch (InterruptedException e) {
                return new ErrorResult(context, "Unable to extract flowfile content due to threading issue");
            }
        } catch (ObjectStorageException e) {
            return new ErrorResult(context, "Unable to get object from content storage", e);
        } catch (IOException e) {
            log.warn("Unable to close input stream from content storage", e);
        } catch (HttpPostException e) {
            return new ErrorResult(context, "Service post failure", e);
        }

        return new EgressResult(context, params.getUrl(), formattedData.getContentReference().getSize());
    }

}
