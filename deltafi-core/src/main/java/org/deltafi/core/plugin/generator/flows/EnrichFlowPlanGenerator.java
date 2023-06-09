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

import org.deltafi.common.types.DomainActionConfiguration;
import org.deltafi.common.types.EnrichActionConfiguration;
import org.deltafi.common.types.EnrichFlowPlan;
import org.deltafi.common.types.FlowPlan;
import org.deltafi.core.plugin.generator.ActionGeneratorInput;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EnrichFlowPlanGenerator {

    /**
     * Generate an enrich flow plan using the given actions. If no actions are provided an empty list is returned.
     * @param baseFlowName prefix to use for the flow plan name
     * @param domainActions domain actions to use in the flow plan
     * @param enrichActions enrich actions to use in the flow plan
     * @return list of flow plans built using the given actions or an empty list if no actions are provided
     */
    List<FlowPlan> generateEnrichFlowPlan(String baseFlowName, List<ActionGeneratorInput> domainActions, List<ActionGeneratorInput> enrichActions) {
        if (ActionUtil.isEmpty(domainActions) && ActionUtil.isEmpty(enrichActions)) {
            return List.of();
        }

        List<DomainActionConfiguration> domainActionConfigs = ActionUtil.domainActionConfigurations(domainActions);
        List<EnrichActionConfiguration> enrichActionConfigs = ActionUtil.enrichActionConfigurations(enrichActions);

        return List.of(generateEnrichFlow(baseFlowName, domainActionConfigs, enrichActionConfigs));
    }

    private FlowPlan generateEnrichFlow(String baseFlowName, List<DomainActionConfiguration> domainActions, List<EnrichActionConfiguration> enrichActions) {
        String planName = baseFlowName + "-enrich";
        EnrichFlowPlan enrichFlowPlan = new EnrichFlowPlan(planName, "Sample enrich flow");

        enrichFlowPlan.setDomainActions(domainActions);
        enrichFlowPlan.setEnrichActions(enrichActions);

        return enrichFlowPlan;
    }

}
