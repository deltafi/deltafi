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
package org.deltafi.core.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.services.FlowDefinitionService;
import org.deltafi.core.types.DeltaFileFlow;
import org.deltafi.core.types.FlowDefinition;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class DeltaFileFlowDeserializer extends JsonDeserializer<DeltaFileFlow> {
    private final FlowDefinitionService flowDefinitionService;
    private final ObjectMapper plainMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public DeltaFileFlow deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectCodec codec = p.getCodec();
        JsonNode node = codec.readTree(p);

        if (node.get("flowDefinition") == null) {
            FlowDefinition flowDef = flowDefinitionService.getOrCreateFlow(
                    node.get("name").asText(),
                    FlowType.valueOf(node.get("type").asText())
            );

            ObjectNode flowDefNode = plainMapper.createObjectNode()
                    .put("id", flowDef.getId())
                    .put("name", flowDef.getName())
                    .put("type", flowDef.getType().name());

            ((ObjectNode) node).set("flowDefinition", flowDefNode);
        }

        return plainMapper.treeToValue(node, DeltaFileFlow.class);
    }
}
