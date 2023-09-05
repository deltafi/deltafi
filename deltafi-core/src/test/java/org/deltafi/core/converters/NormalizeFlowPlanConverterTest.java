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
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.common.types.LoadActionConfiguration;
import org.deltafi.common.types.TransformActionConfiguration;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.types.NormalizeFlow;
import org.deltafi.common.types.NormalizeFlowPlan;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class NormalizeFlowPlanConverterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    final NormalizeFlowPlanConverter normalizeFlowPlanConverter = new NormalizeFlowPlanConverter();

    @Test
    void testConverter() throws IOException {
        NormalizeFlowPlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-ingress-flowplan-test.json"), NormalizeFlowPlan.class);

        NormalizeFlow normalizeFlow = normalizeFlowPlanConverter.convert(flowPlan, variables());

        assertThat(normalizeFlow.getName()).isEqualTo("passthrough");
        assertThat(normalizeFlow.getDescription()).isEqualTo("Flow that passes data through unchanged");
        assertThat(normalizeFlow.getFlowStatus().getState()).isEqualTo(FlowState.STOPPED);
        assertThat(normalizeFlow.getFlowStatus().getTestMode()).isFalse();
        assertThat(normalizeFlow.getFlowStatus().getErrors()).isEmpty();
        assertThat(normalizeFlow.getSourcePlugin()).isEqualTo(expectedSourcePlugin());
        assertThat(normalizeFlow.getTransformActions()).hasSize(1).contains(expectedTransform());
        assertThat(normalizeFlow.getLoadAction()).isEqualTo(expectedLoadAction());
    }

    @Test
    void testUnresolvedPlaceholder() throws IOException {
        NormalizeFlowPlan normalizeFlowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-ingress-flowplan-unresolved-test.json"), NormalizeFlowPlan.class);

        NormalizeFlow normalizeFlow = normalizeFlowPlanConverter.convert(normalizeFlowPlan, variables());

        assertThat(normalizeFlow.getFlowStatus().getState()).isEqualTo(FlowState.INVALID);
        FlowConfigError expected = FlowConfigError.newBuilder()
                .configName("${missing.placeholder:defaultignored}")
                .errorType(FlowErrorType.UNRESOLVED_VARIABLE).message("Could not find a variable named 'missing.placeholder:defaultignored' used in value \"${missing.placeholder:defaultignored}\"").build();

        assertThat(normalizeFlow.getFlowStatus().getErrors()).hasSize(1).contains(expected);

    }

    TransformActionConfiguration expectedTransform() {
        TransformActionConfiguration expected = new TransformActionConfiguration("PassthroughTransformAction", "org.deltafi.passthrough.action.RoteTransformAction");
        expected.setInternalParameters(Map.of("resultType", "passthrough-binary", "someString", "[a, b, c]"));
        expected.setParameters(Map.of("resultType", "passthrough-binary", "someString", "[a, b, c]"));
        return expected;
    }

    LoadActionConfiguration expectedLoadAction() {
        LoadActionConfiguration loadActionConfiguration = new LoadActionConfiguration("PassthroughLoadAction", "org.deltafi.passthrough.action.RoteLoadAction");
        loadActionConfiguration.setInternalParameters(Map.of("domains", List.of("binary")));
        loadActionConfiguration.setParameters(Map.of("domains", List.of("binary")));
        return loadActionConfiguration;
    }

    PluginCoordinates expectedSourcePlugin() {
        return PluginCoordinates.builder().artifactId("deltafi-passthrough").version("0.17.0").groupId("org.deltafi.passthrough").build();
    }

    List<Variable> variables() {
        return List.of(
                Variable.builder().name("incoming.type").defaultValue("binary").dataType(VariableDataType.STRING).build(),
                Variable.builder().name("transform.produces").defaultValue("passthrough-binary").dataType(VariableDataType.STRING).build(),
                Variable.builder().name("domain.type").defaultValue("binary").dataType(VariableDataType.STRING).build(),
                Variable.builder().name("enrichment.value").defaultValue("enrichment value").dataType(VariableDataType.STRING).value("customized enrichment value").build(),
                Variable.builder().name("egressUrl").defaultValue("http://deltafi-egress-sink-service").dataType(VariableDataType.STRING).build());
    }
}