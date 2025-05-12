/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.common.http.HttpService;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class HttpEgress extends HttpEgressBase<HttpEgress.Parameters> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public HttpEgress(HttpService httpService) {
        super("Egresses to an HTTP endpoint.", httpService);
    }

    @Override
    public EgressResultType egress(@NotNull ActionContext context, @NotNull HttpEgress.Parameters params, @NotNull EgressInput input) {
        return doEgress(context, params, params.getMethod(), input);
    }

    @Override
    protected Map<String, String> buildHeaders(@NotNull ActionContext context, @NotNull HttpEgress.Parameters params,
                                               @NotNull EgressInput input) throws JsonProcessingException {
        Map<String, String> headers = new HashMap<>(Map.of(params.getMetadataKey(),
                OBJECT_MAPPER.writeValueAsString(StandardEgressHeaders.buildMap(context, input))));
        if (params.getExtraHeaders() != null) {
            headers.putAll(params.getExtraHeaders());
        }
        return headers;
    }

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    public static class Parameters extends HttpEgressParameters {
        @JsonProperty(required = true)
        @JsonPropertyDescription("Send metadata as JSON in this HTTP header field")
        private String metadataKey;

        @JsonProperty(defaultValue = "POST")
        @JsonPropertyDescription("HTTP method to use when sending the data: DELETE, PATCH, POST, or PUT")
        private HttpRequestMethod method = HttpRequestMethod.POST;

        @JsonPropertyDescription("Additional key/value pairs to set in the HTTP header")
        private Map<String, String> extraHeaders;
    }
}
