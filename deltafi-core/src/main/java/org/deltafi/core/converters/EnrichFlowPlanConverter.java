/**
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

import org.deltafi.common.types.EnrichActionConfiguration;
import org.deltafi.common.types.DomainActionConfiguration;
import org.deltafi.common.types.KeyValue;
import org.deltafi.core.types.EnrichFlow;
import org.deltafi.common.types.EnrichFlowPlan;

import java.util.List;

public class EnrichFlowPlanConverter extends FlowPlanConverter<EnrichFlowPlan, EnrichFlow> {

    @Override
    public void populateFlowSpecificFields(EnrichFlowPlan flowPlan, EnrichFlow flow, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        flow.setDomainActions(buildDomainActions(flowPlan.getDomainActions(), flowPlanPropertyHelper));
        flow.setEnrichActions(buildEnrichActions(flowPlan.getEnrichActions(), flowPlanPropertyHelper));
    }

    List<DomainActionConfiguration> buildDomainActions(List<DomainActionConfiguration> domainActionTemplates, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        return null != domainActionTemplates ? domainActionTemplates.stream()
                .map(domainActionTemplate -> buildDomainAction(domainActionTemplate, flowPlanPropertyHelper))
                .toList() : List.of();
    }

    List<EnrichActionConfiguration> buildEnrichActions(List<EnrichActionConfiguration> enrichActionTemplates, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        return null != enrichActionTemplates ? enrichActionTemplates.stream()
                .map(enrichActionTemplate -> buildEnrichAction(enrichActionTemplate, flowPlanPropertyHelper))
                .toList() : List.of();
    }

    /**
     * Return a copy of the domain action configuration with placeholders resolved where possible.
     *
     * @param domainActionTemplate template of the DomainActionConfiguration that should be created
     * @return DomainActionConfiguration with variable values substituted in
     */
    DomainActionConfiguration buildDomainAction(DomainActionConfiguration domainActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        String replacedName = flowPlanPropertyHelper.getReplacedName(domainActionTemplate);
        List<String> requiresDomains = flowPlanPropertyHelper.replaceListOfPlaceholders(domainActionTemplate.getRequiresDomains(), replacedName);
        DomainActionConfiguration domainActionConfiguration = new DomainActionConfiguration(replacedName, domainActionTemplate.getType(), requiresDomains);
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(domainActionConfiguration, domainActionTemplate);
        return domainActionConfiguration;
    }

    /**
     * Return a copy of the enrich action configuration with placeholders resolved where possible.
     *
     * @param enrichActionTemplate template of the EnrichActionConfiguration that should be created
     * @return EnrichActionConfiguration with variable values substituted in
     */
    EnrichActionConfiguration buildEnrichAction(EnrichActionConfiguration enrichActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        String replacedName = flowPlanPropertyHelper.getReplacedName(enrichActionTemplate);
        List<String> requiresDomains = flowPlanPropertyHelper.replaceListOfPlaceholders(enrichActionTemplate.getRequiresDomains(), replacedName);
        EnrichActionConfiguration enrichActionConfiguration = new EnrichActionConfiguration(replacedName, enrichActionTemplate.getType(), requiresDomains);
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(enrichActionConfiguration, enrichActionTemplate);

        List<String> requiresEnrichments = flowPlanPropertyHelper.replaceListOfPlaceholders(enrichActionTemplate.getRequiresEnrichments(), enrichActionConfiguration.getName());
        List<KeyValue> requiresMetadata = flowPlanPropertyHelper.replaceKeyValuePlaceholders(enrichActionTemplate.getRequiresMetadataKeyValues(), enrichActionConfiguration.getName());
        enrichActionConfiguration.setRequiresEnrichments(requiresEnrichments);
        enrichActionConfiguration.setRequiresMetadataKeyValues(requiresMetadata);

        return enrichActionConfiguration;
    }

    @Override
    EnrichFlow getFlowInstance() {
        return new EnrichFlow();
    }

}
