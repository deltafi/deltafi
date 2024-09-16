/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.common.http.HttpService;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;

@Component
@Slf4j
public class DeltaFiEgress extends HttpEgressBase<DeltaFiEgressParameters> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public DeltaFiEgress(HttpService httpService) {
        super("Egresses to another DeltaFi", httpService);
    }

    @Override
    protected Map<String, String> buildHeaders(@NotNull ActionContext context, @NotNull DeltaFiEgressParameters params,
            @NotNull EgressInput input) throws JsonProcessingException {
        Map<String, String> metadataMap = new TreeMap<>(input.getMetadata());
        metadataMap.put("originalDid", context.getDid().toString());
        metadataMap.put("originalSystem", context.getSystemName());

        Map<String, String> headersMap = new TreeMap<>(Map.of("filename", input.getContent().getName(),
                "metadata", OBJECT_MAPPER.writeValueAsString(metadataMap)));

        if (params.getFlow() != null) {
            headersMap.put("flow", params.getFlow());
        }

        log.debug(headersMap.toString());
        return headersMap;
    }
}
