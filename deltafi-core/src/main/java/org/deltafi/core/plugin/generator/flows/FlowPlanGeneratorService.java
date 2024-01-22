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
package org.deltafi.core.plugin.generator.flows;

import org.deltafi.common.types.FlowPlan;
import org.deltafi.common.types.Variable;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.core.plugin.generator.PluginGeneratorInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FlowPlanGeneratorService {

    private static final Variable SAMPLE_STRING_VAR = Variable.builder()
            .name(ActionUtil.SAMPLE_STRING)
            .required(true)
            .defaultValue("sample value")
            .description("Sample string variable")
            .dataType(VariableDataType.STRING).build();

    private static final Variable SAMPLE_NUMBER_VAR = Variable.builder()
            .name(ActionUtil.SAMPLE_NUMBER)
            .required(false)
            .defaultValue("10")
            .description("Sample number variable")
            .dataType(VariableDataType.NUMBER).build();

    private static final Variable SAMPLE_BOOLEAN_VAR = Variable.builder()
            .name(ActionUtil.SAMPLE_BOOLEAN)
            .required(false)
            .defaultValue("true")
            .description("Sample boolean variable")
            .dataType(VariableDataType.BOOLEAN).build();

    private static final Variable SAMPLE_LIST_VAR = Variable.builder()
            .name(ActionUtil.SAMPLE_LIST)
            .required(false)
            .defaultValue("a, b, c")
            .description("Sample list variable")
            .dataType(VariableDataType.LIST).build();

    private static final Variable SAMPLE_MAP_VAR = Variable.builder()
            .name(ActionUtil.SAMPLE_MAP)
            .required(false)
            .defaultValue("a: a, b: b, c: c")
            .description("Sample map variable")
            .dataType(VariableDataType.MAP).build();

    private static final Variable EGRESS_VAR = Variable.builder()
            .name(ActionUtil.EGRESS_VAR_NAME)
            .description("The URL to post the DeltaFile to")
            .dataType(VariableDataType.STRING)
            .required(true)
            .defaultValue("http://deltafi-egress-sink-service")
            .build();

    private static final List<Variable> SAMPLE_VARS = List.of(SAMPLE_STRING_VAR, SAMPLE_NUMBER_VAR, SAMPLE_BOOLEAN_VAR, SAMPLE_LIST_VAR, SAMPLE_MAP_VAR);

    private final TransformFlowPlanGenerator transformFlowPlanGenerator;
    private final EgressFlowPlanGenerator egressFlowPlanGenerator;

    public FlowPlanGeneratorService(TransformFlowPlanGenerator transformFlowPlanGenerator, EgressFlowPlanGenerator egressFlowPlanGenerator) {
        this.transformFlowPlanGenerator = transformFlowPlanGenerator;
        this.egressFlowPlanGenerator = egressFlowPlanGenerator;
    }

    /**
     * Create flow plans using the given plugin generator input. If no actions are provided this will
     * create a single transform flow plan using a default egress action. If any normalization specific actions are
     * provided an ingress and egress flow plan will be created, and optionally an enrich plan if there are
     * any domain or enrich actions provided.
     * @param baseFlowName prefix to use in the flow plan names
     * @param pluginGeneratorInput input containing the actions that should be used in the flow plans
     * @return a list of flow plans
     */
    public List<FlowPlan> generateFlowPlans(String baseFlowName, PluginGeneratorInput pluginGeneratorInput) {
        List<FlowPlan> flowPlans = new ArrayList<>();

        // TODO: timed ingress
        flowPlans.addAll(transformFlowPlanGenerator.generateTransformFlows(baseFlowName, pluginGeneratorInput.getTransformActions()));
        flowPlans.addAll(egressFlowPlanGenerator.generateEgressFlowPlans(baseFlowName, pluginGeneratorInput.getEgressActions()));

        return flowPlans;
    }

    /**
     * Create list of variables. If the default egress action is used, add a variable
     * to set the url. Always include the sample var that is needed when a sample
     * parameter class is generated.
     * @param pluginGeneratorInput input that contains the list of actions used in the flow plans
     * @return a list of variables
     */
    public List<Variable> generateVariables(PluginGeneratorInput pluginGeneratorInput) {
        List<Variable> variables = new ArrayList<>();
        if (pluginGeneratorInput.getEgressActions().isEmpty()) {
            variables.add(EGRESS_VAR);
        }

        variables.addAll(SAMPLE_VARS);
        return variables;
    }

}
