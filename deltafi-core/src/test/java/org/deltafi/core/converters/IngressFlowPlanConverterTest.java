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
package org.deltafi.core.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.resource.Resource;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.common.types.LoadActionConfiguration;
import org.deltafi.common.types.TransformActionConfiguration;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.types.IngressFlow;
import org.deltafi.common.types.IngressFlowPlan;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class IngressFlowPlanConverterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    IngressFlowPlanConverter ingressFlowPlanConverter = new IngressFlowPlanConverter();

    @Test
    void testConverter() throws IOException {
        IngressFlowPlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-ingress-flowplan-test.json"), IngressFlowPlan.class);

        IngressFlow ingressFlow = ingressFlowPlanConverter.convert(flowPlan, variables());

        assertThat(ingressFlow.getName()).isEqualTo("passthrough");
        assertThat(ingressFlow.getDescription()).isEqualTo("Flow that passes data through unchanged");
        assertThat(ingressFlow.getFlowStatus().getState()).isEqualTo(FlowState.STOPPED);
        assertThat(ingressFlow.getFlowStatus().getTestMode()).isFalse();
        assertThat(ingressFlow.getFlowStatus().getErrors()).isEmpty();
        assertThat(ingressFlow.getSourcePlugin()).isEqualTo(expectedSourcePlugin());
        assertThat(ingressFlow.getTransformActions()).hasSize(1).contains(expectedTransform());
        assertThat(ingressFlow.getLoadAction()).isEqualTo(expectedLoadAction());
    }

    @Test
    void testUnresolvedPlaceholder() throws IOException {
        IngressFlowPlan ingressFlowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-ingress-flowplan-unresolved-test.json"), IngressFlowPlan.class);

        IngressFlow ingressFlow = ingressFlowPlanConverter.convert(ingressFlowPlan, variables());

        assertThat(ingressFlow.getFlowStatus().getState()).isEqualTo(FlowState.INVALID);
        FlowConfigError expected = FlowConfigError.newBuilder()
                .configName("${missing.placeholder:defaultignored}")
                .errorType(FlowErrorType.UNRESOLVED_VARIABLE).message("Could not resolve placeholder 'missing.placeholder:defaultignored' in value \"${missing.placeholder:defaultignored}\"").build();

        assertThat(ingressFlow.getFlowStatus().getErrors()).hasSize(1).contains(expected);

    }

    TransformActionConfiguration expectedTransform() {
        TransformActionConfiguration expected = new TransformActionConfiguration("passthrough.PassthroughTransformAction", "org.deltafi.passthrough.action.RoteTransformAction");
        expected.setParameters(Map.of("resultType", "passthrough-binary"));
        return expected;
    }

    LoadActionConfiguration expectedLoadAction() {
        LoadActionConfiguration loadActionConfiguration = new LoadActionConfiguration("passthrough.PassthroughLoadAction", "org.deltafi.passthrough.action.RoteLoadAction");
        loadActionConfiguration.setParameters(Map.of("domains", List.of("binary")));
        return loadActionConfiguration;
    }

    PluginCoordinates expectedSourcePlugin() {
        return PluginCoordinates.builder().artifactId("deltafi-passthrough").version("0.17.0").groupId("org.deltafi.passthrough").build();
    }

    List<Variable> variables() {
        return List.of(
                Variable.newBuilder().name("incoming.type").defaultValue("binary").dataType(VariableDataType.STRING).build(),
                Variable.newBuilder().name("transform.produces").defaultValue("passthrough-binary").dataType(VariableDataType.STRING).build(),
                Variable.newBuilder().name("domain.type").defaultValue("binary").dataType(VariableDataType.STRING).build(),
                Variable.newBuilder().name("enrichment.value").defaultValue("enrichment value").dataType(VariableDataType.STRING).value("customized enrichment value").build(),
                Variable.newBuilder().name("egressUrl").defaultValue("http://deltafi-egress-sink-service").dataType(VariableDataType.STRING).build());
    }
}