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

import org.deltafi.common.types.LoadActionConfiguration;
import org.deltafi.common.types.NormalizeFlowPlan;
import org.deltafi.common.types.TransformActionConfiguration;
import org.deltafi.core.types.NormalizeFlow;

import java.util.List;
import java.util.Objects;

public class NormalizeFlowPlanConverter extends FlowPlanConverter<NormalizeFlowPlan, NormalizeFlow> {

    public void populateFlowSpecificFields(NormalizeFlowPlan normalizeFlowPlan, NormalizeFlow normalizeFlow, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        if (normalizeFlowPlan.getLoadAction() != null) {
            normalizeFlow.setLoadAction(buildLoadAction(normalizeFlowPlan.getLoadAction(), flowPlanPropertyHelper));
        }
        normalizeFlow.setTransformActions(buildTransformActions(normalizeFlowPlan.getTransformActions(), flowPlanPropertyHelper));
    }

    /**
     * Return a copy of the load action configuration with placeholders resolved where possible.
     *
     * @param loadActionTemplate template of the LoadActionConfiguration that should be created
     * @return LoadActionConfiguration with variable values substituted in
     */
    LoadActionConfiguration buildLoadAction(LoadActionConfiguration loadActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        LoadActionConfiguration loadActionConfiguration = new LoadActionConfiguration(
                flowPlanPropertyHelper.getReplacedName(loadActionTemplate), loadActionTemplate.getType());
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(loadActionConfiguration, loadActionTemplate);
        return loadActionConfiguration;
    }

    List<TransformActionConfiguration> buildTransformActions(List<TransformActionConfiguration> transformActionTemplates, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        return Objects.nonNull(transformActionTemplates) ? transformActionTemplates.stream()
                    .map(transformTemplate -> buildTransformAction(transformTemplate, flowPlanPropertyHelper))
                    .toList() : List.of();
    }

    /**
     * Return a copy of the transform action configuration with placeholders resolved where possible.
     *
     * @param transformActionTemplate template of the TransformActionConfiguration that should be created
     * @return TransformActionConfiguration with variable values substituted in
     */
    TransformActionConfiguration buildTransformAction(TransformActionConfiguration transformActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        TransformActionConfiguration transformActionConfiguration = new TransformActionConfiguration(
                flowPlanPropertyHelper.getReplacedName(transformActionTemplate), transformActionTemplate.getType());
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(transformActionConfiguration, transformActionTemplate);
        return transformActionConfiguration;
    }

    @Override
    NormalizeFlow getFlowInstance() {
        return new NormalizeFlow();
    }
}
