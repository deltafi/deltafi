/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.egress;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.http.HttpPostException;
import org.deltafi.common.nifi.FlowFileInputStream;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.HttpEgressParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.deltafi.common.nifi.ContentType.APPLICATION_FLOWFILE;

@Component
@Slf4j
public class FlowfileEgress extends HttpEgressBase<HttpEgressParameters> {
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);

    public FlowfileEgress() {
        super(String.format("Egresses content and attributes in a NiFi V1 FlowFile (%s)", APPLICATION_FLOWFILE));
    }

    protected EgressResultType doEgress(@NotNull ActionContext context, @NotNull HttpEgressParameters params,
            @NotNull EgressInput input) {
        try (InputStream inputStream = input.getContent().loadInputStream()) {
            Map<String, String> attributes = buildHeadersMap(context.getDid(), context.getDeltaFileName(),
                    input.getContent().getName(), context.getDataSource(), context.getFlowName(), input.getMetadata());

            HttpResponse<InputStream> response;

            try (FlowFileInputStream flowFileInputStream = new FlowFileInputStream()) {
                flowFileInputStream.runPipeWriter(inputStream, attributes, input.getContent().getSize(), executorService);
                response = httpPostService.post(params.getUrl(), Map.of(), flowFileInputStream, APPLICATION_FLOWFILE);
            } catch (IOException e) {
                return new ErrorResult(context, "Unable to process flowfile stream", e);
            }

            Response.Status status = Response.Status.fromStatusCode(response.statusCode());
            if (Objects.isNull(status) || status.getFamily() != Response.Status.Family.SUCCESSFUL) {
                try (InputStream body = response.body()) {
                    return new ErrorResult(context, "Unsuccessful HTTP POST: " + response.statusCode(),
                            new String(body.readAllBytes())).logErrorTo(log);
                }
            }
        } catch (IOException e) {
            log.warn("Unable to close input stream from content storage", e);
        } catch (HttpPostException e) {
            return new ErrorResult(context, "Service post failure", e);
        }

        return new EgressResult(context, params.getUrl(), input.getContent().getSize());
    }
}
