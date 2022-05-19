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

import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.configuration.EnrichActionConfiguration;
import org.deltafi.core.domain.types.EnrichFlow;
import org.deltafi.core.domain.types.EnrichFlowPlan;

import java.util.List;
import java.util.stream.Collectors;

public class EnrichFlowPlanConverter extends FlowPlanConverter<EnrichFlowPlan, EnrichFlow> {

    @Override
    public void populateFlowSpecificFields(EnrichFlowPlan flowPlan, EnrichFlow flow, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        flow.setEnrichActions(buildEnrichActions(flowPlan.getEnrichActions(), flowPlanPropertyHelper));
    }

    List<EnrichActionConfiguration> buildEnrichActions(List<EnrichActionConfiguration> enrichActionTemplates, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        return enrichActionTemplates.stream()
                .map(enrichActionTemplate -> buildEnrichAction(enrichActionTemplate, flowPlanPropertyHelper))
                .collect(Collectors.toList());
    }

    /**
     * Return a copy of the enrich action configuration with placeholders resolved where possible.
     *
     * @param enrichActionTemplate template of the EnrichActionConfiguration that should be created
     * @return EnrichActionConfiguration with variable values substituted in
     */
    EnrichActionConfiguration buildEnrichAction(EnrichActionConfiguration enrichActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        EnrichActionConfiguration enrichActionConfiguration = new EnrichActionConfiguration();
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(enrichActionConfiguration, enrichActionTemplate);

        List<String> requiresEnrichment = flowPlanPropertyHelper.replaceListOfPlaceholders(enrichActionTemplate.getRequiresEnrichment(), enrichActionConfiguration.getName());
        List<String> requiresDomains = flowPlanPropertyHelper.replaceListOfPlaceholders(enrichActionTemplate.getRequiresDomains(), enrichActionConfiguration.getName());
        List<KeyValue> requiresMetadata = flowPlanPropertyHelper.replaceKeyValuePlaceholders(enrichActionTemplate.getRequiresMetadataKeyValues(), enrichActionConfiguration.getName());

        enrichActionConfiguration.setRequiresEnrichment(requiresEnrichment);
        enrichActionConfiguration.setRequiresDomains(requiresDomains);
        enrichActionConfiguration.setRequiresMetadataKeyValues(requiresMetadata);

        return enrichActionConfiguration;
    }

    @Override
    EnrichFlow getFlowInstance() {
        return new EnrichFlow();
    }

}
