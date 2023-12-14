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

import org.deltafi.common.types.*;
import org.deltafi.core.types.TransformFlow;

import java.util.List;
import java.util.Objects;

public class TransformFlowPlanConverter extends FlowPlanConverter<TransformFlowPlan, TransformFlow> {

    public void populateFlowSpecificFields(TransformFlowPlan transformFlowPlan, TransformFlow transformFlow, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        transformFlow.setTransformActions(buildTransformActions(transformFlowPlan.getTransformActions(), flowPlanPropertyHelper));
        transformFlow.setEgressAction(buildEgressAction(transformFlowPlan.getEgressAction(), flowPlanPropertyHelper));
        transformFlow.setSubscriptions(transformFlowPlan.getSubscriptions());
    }

    /**
     * Return a copy of the egress action configuration with placeholders resolved where possible.
     *
     * @param egressActionTemplate template of the EgressActionConfiguration that should be created
     * @return EgressActionConfiguration with variable values substituted in
     */
    EgressActionConfiguration buildEgressAction(EgressActionConfiguration egressActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        EgressActionConfiguration egressActionConfiguration = new EgressActionConfiguration(
                flowPlanPropertyHelper.getReplacedName(egressActionTemplate), egressActionTemplate.getType());
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(egressActionConfiguration, egressActionTemplate);
        return egressActionConfiguration;
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
    TransformFlow getFlowInstance() {
        return new TransformFlow();
    }
}
