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
import org.deltafi.core.domain.types.IngressFlow;
import org.deltafi.core.domain.types.IngressFlowPlan;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class IngressFlowPlanConverter extends FlowPlanConverter<IngressFlowPlan, IngressFlow> {

    public void populateFlowSpecificFields(IngressFlowPlan ingressFlowPlan, IngressFlow ingressFlow, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        ingressFlow.setType(flowPlanPropertyHelper.replaceValue(ingressFlowPlan.getType(), ingressFlow.getName()));
        ingressFlow.setLoadAction(buildLoadAction(ingressFlowPlan.getLoadAction(), flowPlanPropertyHelper));
        ingressFlow.setTransformActions(buildTransformActions(ingressFlowPlan.getTransformActions(), flowPlanPropertyHelper));
    }

    /**
     * Return a copy of the load action configuration with placeholders resolved where possible.
     *
     * @param loadActionTemplate template of the LoadActionConfiguration that should be created
     * @return LoadActionConfiguration with variable values substituted in
     */
    LoadActionConfiguration buildLoadAction(LoadActionConfiguration loadActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        LoadActionConfiguration loadActionConfiguration = new LoadActionConfiguration();
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(loadActionConfiguration, loadActionTemplate);
        loadActionConfiguration.setConsumes(loadActionTemplate.getConsumes());
        return loadActionConfiguration;
    }

    List<TransformActionConfiguration> buildTransformActions(List<TransformActionConfiguration> transformActionTemplates, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        return Objects.nonNull(transformActionTemplates) ? transformActionTemplates.stream()
                    .map(transformTemplate -> buildTransformAction(transformTemplate, flowPlanPropertyHelper))
                    .collect(Collectors.toList()) : List.of();
    }

    /**
     * Return a copy of the transform action configuration with placeholders resolved where possible.
     *
     * @param transformActionTemplate template of the TransformActionConfiguration that should be created
     * @return TransformActionConfiguration with variable values substituted in
     */
    TransformActionConfiguration buildTransformAction(TransformActionConfiguration transformActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        TransformActionConfiguration transformActionConfiguration = new TransformActionConfiguration();

        flowPlanPropertyHelper.replaceCommonActionPlaceholders(transformActionConfiguration, transformActionTemplate);
        transformActionConfiguration.setConsumes(transformActionTemplate.getConsumes());
        transformActionConfiguration.setProduces(transformActionTemplate.getProduces());

        return transformActionConfiguration;
    }

    @Override
    IngressFlow getFlowInstance() {
        return new IngressFlow();
    }
}
