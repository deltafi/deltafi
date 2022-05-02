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
package org.deltafi.core.domain.converters;

import org.deltafi.core.domain.configuration.LoadActionConfiguration;
import org.deltafi.core.domain.configuration.TransformActionConfiguration;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.generated.types.Variable;
import org.deltafi.core.domain.types.IngressFlow;
import org.deltafi.core.domain.types.IngressFlowPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IngressFlowPlanConverter {

    /**
     * Convert the given IngressFlowPlan to an IngressFlow using the given variables
     * to resolve any placeholders in the plan.
     * @param ingressFlowPlan IngressFlowPlan that will be used to create the ingress flow
     * @param variables list of variables that should be used in the IngressFlow
     * @return populated IngressFlow that can be turned on or off if it is valid
     */
    public IngressFlow toIngressFlow(IngressFlowPlan ingressFlowPlan, List<Variable> variables) {

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(variables, ingressFlowPlan.getName());

        IngressFlow ingressFlow = new IngressFlow();

        ingressFlow.setDescription(ingressFlowPlan.getDescription());
        ingressFlow.setName(ingressFlowPlan.getName());
        ingressFlow.setSourcePlugin(ingressFlowPlan.getSourcePlugin());
        ingressFlow.setType(flowPlanPropertyHelper.replaceValue(ingressFlowPlan.getType(), ingressFlow.getName()));

        ingressFlow.setLoadAction(buildLoadAction(ingressFlowPlan.getLoadAction(), flowPlanPropertyHelper));

        List<TransformActionConfiguration> transformActionConfigurations = new ArrayList<>();

        if (Objects.nonNull(ingressFlowPlan.getTransformActions())) {
            ingressFlowPlan.getTransformActions().stream()
                    .map(transformTemplate -> buildTransformAction(transformTemplate, flowPlanPropertyHelper))
                    .forEach(transformActionConfigurations::add);
        }

        ingressFlow.setTransformActions(transformActionConfigurations);

        List<FlowConfigError> configErrors = new ArrayList<>(flowPlanPropertyHelper.getErrors());

        FlowState state = configErrors.isEmpty() ? FlowState.STOPPED : FlowState.INVALID;
        ingressFlow.getFlowStatus().setState(state);
        ingressFlow.getFlowStatus().getErrors().addAll(configErrors);

        ingressFlow.setVariables(flowPlanPropertyHelper.getAppliedVariables());

        return ingressFlow;
    }

    /**
     * Return a copy of the load action configuration with placeholders resolved where possible.
     *
     * @param loadActionTemplate template of the LoadActionConfiguration that should be created
     * @return LoadActionConfiguration with variable values substituted in
     */
    LoadActionConfiguration buildLoadAction(org.deltafi.core.domain.generated.types.LoadActionConfiguration loadActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        LoadActionConfiguration loadActionConfiguration = new LoadActionConfiguration();
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(loadActionConfiguration, loadActionTemplate);
        loadActionConfiguration.setConsumes(loadActionTemplate.getConsumes());
        return loadActionConfiguration;
    }

    /**
     * Return a copy of the transform action configuration with placeholders resolved where possible.
     *
     * @param transformActionTemplate template of the TransformActionConfiguration that should be created
     * @return TransformActionConfiguration with variable values substituted in
     */
    TransformActionConfiguration buildTransformAction(org.deltafi.core.domain.generated.types.TransformActionConfiguration transformActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        TransformActionConfiguration transformActionConfiguration = new TransformActionConfiguration();

        flowPlanPropertyHelper.replaceCommonActionPlaceholders(transformActionConfiguration, transformActionTemplate);
        transformActionConfiguration.setConsumes(transformActionTemplate.getConsumes());
        transformActionConfiguration.setProduces(transformActionTemplate.getProduces());

        return transformActionConfiguration;
    }

}
