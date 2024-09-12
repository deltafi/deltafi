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
package org.deltafi.core.converters;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.resource.Resource;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.EgressFlowPlanEntity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class EgressFlowPlanConverterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .registerModule(new JavaTimeModule());
    final EgressFlowPlanConverter egressFlowPlanConverter = new EgressFlowPlanConverter();

    @Test
    void testConverter() throws IOException {
        EgressFlowPlanEntity flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-egress-flowplan-test.json"), EgressFlowPlanEntity.class);
        EgressFlow egressFlow = egressFlowPlanConverter.convert(flowPlan, variables());

        assertThat(egressFlow.getName()).isEqualTo("passthrough");
        assertThat(egressFlow.getEgressAction()).isEqualTo(expectedEgressAction());
        assertThat(egressFlow.getFlowStatus().getState()).isEqualTo(FlowState.STOPPED);
        assertThat(egressFlow.getFlowStatus().getTestMode()).isFalse();
    }

    @Test
    void testUnresolvedPlaceholder() throws IOException {
        EgressFlowPlanEntity flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-egress-flowplan-unresolved-test.json"), EgressFlowPlanEntity.class);
        EgressFlow egressFlow = egressFlowPlanConverter.convert(flowPlan, variables());

        assertThat(egressFlow.getFlowStatus().getState()).isEqualTo(FlowState.INVALID);
        FlowConfigError expected = FlowConfigError.newBuilder()
                .configName("${missing.placeholder:defaultignored}")
                .errorType(FlowErrorType.UNRESOLVED_VARIABLE).message("Could not find a variable named 'missing.placeholder:defaultignored' used in value \"${missing.placeholder:defaultignored}\"").build();

        assertThat(egressFlow.getFlowStatus().getErrors()).hasSize(1).contains(expected);
    }

    @Test
    void testBuildFlowList_replaceCommaSeperatedList() {
        List<Variable> variables = new ArrayList<>(variables());
        variables.add(Variable.builder().name("flows").value("b, c,  d ").dataType(VariableDataType.LIST).build());

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(variables);
        List<String> input = List.of("${flows}");
        List<String> output = egressFlowPlanConverter.buildFlowList(input, flowPlanPropertyHelper, "plan");
        assertThat(output).isEqualTo(List.of("b", "c", "d"));
    }

    @Test
    void testBuildFlowList_replaceCommaSeperatedListAndConstant() {
        List<Variable> variables = new ArrayList<>(variables());
        variables.add(Variable.builder().name("flows").value("b, c ").dataType(VariableDataType.LIST).build());

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(variables);
        List<String> input = List.of("a", "${flows}", "d");
        List<String> output = egressFlowPlanConverter.buildFlowList(input, flowPlanPropertyHelper, "plan");
        assertThat(output).isEqualTo(List.of("a", "b", "c", "d"));
    }

    @Test
    void testBuildFlowList_null() {
        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(variables());
        List<String> output = egressFlowPlanConverter.buildFlowList(null, flowPlanPropertyHelper, "plan");
        assertThat(output).isNull();
    }

    ActionConfiguration expectedEgressAction() {
        ActionConfiguration ActionConfiguration = new ActionConfiguration("PassthroughEgressAction", ActionType.EGRESS, "org.deltafi.core.action.RestPostEgressAction");
        ActionConfiguration.setInternalParameters(Map.of("egressFlow", "egressFlow", "metadataKey", "deltafiMetadata", "url", "http://deltafi-egress-sink-service"));
        ActionConfiguration.setParameters(Map.of("egressFlow", "egressFlow", "metadataKey", "deltafiMetadata", "url", "http://deltafi-egress-sink-service"));
        return ActionConfiguration;
    }

    List<Variable> variables() {
        return List.of(
                Variable.builder().name("incoming.type").defaultValue("binary").dataType(VariableDataType.STRING).build(),
                Variable.builder().name("transform.produces").defaultValue("passthrough-binary").dataType(VariableDataType.STRING).build(),
                Variable.builder().name("domain.type").defaultValue("binary").dataType(VariableDataType.STRING).build(),
                Variable.builder().name("enrichment.value").defaultValue("enrichment value").value("customized enrichment value").dataType(VariableDataType.MAP).build(),
                Variable.builder().name("egressUrl").defaultValue("http://deltafi-egress-sink-service").dataType(VariableDataType.STRING).build(),
                Variable.builder().name("passthrough.includeIngressFlows").value(null).dataType(VariableDataType.LIST).build(),
                Variable.builder().name("passthrough.excludeIngressFlows").value("a, b, c").dataType(VariableDataType.LIST).build());
    }
}