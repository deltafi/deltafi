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
package org.deltafi.core.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.resource.Resource;
import org.deltafi.common.types.Variable;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.common.types.EnrichActionConfiguration;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.types.EnrichFlow;
import org.deltafi.common.types.EnrichFlowPlan;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class EnrichFlowPlanConverterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    final EnrichFlowPlanConverter enrichFlowPlanConverter = new EnrichFlowPlanConverter();

    @Test
    void testConverter() throws IOException {
        EnrichFlowPlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-enrich-flowplan-test.json"), EnrichFlowPlan.class);
        EnrichFlow enrichFlow = enrichFlowPlanConverter.convert(flowPlan, variables());

        assertThat(enrichFlow.getName()).isEqualTo("passthrough");
        assertThat(enrichFlow.getDescription()).isEqualTo("Flow that passes data through unchanged");
        assertThat(enrichFlow.getEnrichActions()).hasSize(1).contains(expectedEnrichAction());
    }

    @Test
    void testUnresolvedPlaceholder() throws IOException {
        EnrichFlowPlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-enrich-flowplan-unresolved-test.json"), EnrichFlowPlan.class);
        EnrichFlow enrichFlow = enrichFlowPlanConverter.convert(flowPlan, variables());

        assertThat(enrichFlow.getFlowStatus().getState()).isEqualTo(FlowState.INVALID);
        FlowConfigError expected = FlowConfigError.newBuilder()
                .configName("${missing.placeholder:defaultignored}")
                .errorType(FlowErrorType.UNRESOLVED_VARIABLE).message("Could not resolve placeholder 'missing.placeholder:defaultignored' in value \"${missing.placeholder:defaultignored}\"").build();

        assertThat(enrichFlow.getFlowStatus().getErrors()).hasSize(1).contains(expected);
    }

    EnrichActionConfiguration expectedEnrichAction() {
        EnrichActionConfiguration enrichActionConfiguration = new EnrichActionConfiguration("passthrough.PassthroughEnrichAction", "org.deltafi.passthrough.action.RoteEnrichAction", List.of("binary"));
        enrichActionConfiguration.setRequiresEnrichments(List.of("binary"));
        enrichActionConfiguration.setParameters(Map.of("enrichments", Map.of("passthroughEnrichment", "customized enrichment value")));
        return enrichActionConfiguration;
    }

    List<Variable> variables() {
        return List.of(
                Variable.newBuilder().name("incoming.type").defaultValue("binary").dataType(VariableDataType.STRING).build(),
                Variable.newBuilder().name("transform.produces").defaultValue("passthrough-binary").dataType(VariableDataType.STRING).build(),
                Variable.newBuilder().name("domain.type").defaultValue("binary").dataType(VariableDataType.STRING).build(),
                Variable.newBuilder().name("enrichment.value").defaultValue("enrichment value").value("customized enrichment value").dataType(VariableDataType.STRING).build(),
                Variable.newBuilder().name("enrichUrl").defaultValue("http://deltafi-enrich-sink-service").dataType(VariableDataType.STRING).build());
    }
}