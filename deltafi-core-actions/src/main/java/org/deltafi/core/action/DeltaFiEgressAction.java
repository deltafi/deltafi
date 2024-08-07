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
package org.deltafi.core.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.http.HttpPostException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.DeltaFiEgressParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class DeltaFiEgressAction extends HttpEgressActionBase<DeltaFiEgressParameters> {
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public DeltaFiEgressAction() {
        super("Egresses to another DeltaFi");
    }

    protected EgressResultType doEgress(@NotNull ActionContext context, @NotNull DeltaFiEgressParameters params, @NotNull EgressInput input) {
        try (InputStream inputStream = input.content().loadInputStream()) {
            HttpResponse<InputStream> response = httpPostService.post(params.getUrl(), buildHeaders(input.content().getName(), params.getFlow(), input.getMetadata(), context), inputStream, input.content().getMediaType());
            Response.Status status = Response.Status.fromStatusCode(response.statusCode());
            if (Objects.isNull(status) || status.getFamily() != Response.Status.Family.SUCCESSFUL) {
                try (InputStream body = response.body()) {
                    return new ErrorResult(context, "Unsuccessful POST: " + response.statusCode() + " " + new String(body.readAllBytes())).logErrorTo(log);
                }
            }
        } catch (JsonProcessingException e) {
            return new ErrorResult(context, "Unable to build post headers", e).logErrorTo(log);
        } catch (IOException e) {
            log.warn("Unable to close input stream from content storage", e);
        } catch (HttpPostException e) {
            return new ErrorResult(context, "Service post failure", e).logErrorTo(log);
        }

        return new EgressResult(context, params.getUrl(), input.content().getSize());
    }

    private Map<String, String> buildHeaders(@NotNull String filename, String flow, Map<String, String> metadata, ActionContext context) throws JsonProcessingException {
        Map<String, String> metadataMap = new HashMap<>(metadata);
        metadataMap.put("originalDid", context.getDid());
        metadataMap.put("originalSystem", context.getSystemName());
        String metadataJson = OBJECT_MAPPER.writeValueAsString(metadataMap);

        Map<String, String> headersMap = new HashMap<>(Map.of("filename", filename, "metadata", metadataJson));
        if (flow != null) {
            headersMap.put("flow", flow);
        }

        log.debug(headersMap.toString());
        return headersMap;
    }
}
