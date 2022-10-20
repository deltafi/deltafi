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
package org.deltafi.core.validation;

import org.assertj.core.api.Assertions;
import org.deltafi.common.types.DomainActionConfiguration;
import org.deltafi.common.types.EnrichActionConfiguration;
import org.deltafi.common.types.EnrichFlowPlan;
import org.deltafi.core.exceptions.DeltafiConfigurationException;
import org.deltafi.core.services.EnrichFlowPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class EnrichFlowPlanValidatorTest {

    @InjectMocks
    EnrichFlowPlanValidator enrichFlowPlanValidator;

    @Mock
    EnrichFlowPlanService enrichFlowPlanService;

    @Test
    void canLoadFlowPlan_noErrors() {
        Mockito.when(enrichFlowPlanService.getAll()).thenReturn(List.of());
        Assertions.assertThatNoException().isThrownBy(() -> enrichFlowPlanValidator.validate(enrichFlowPlan()));
    }

    @Test
    void canLoadFlowPlan_noActions() {
        EnrichFlowPlan enrichFlowPlan = enrichFlowPlan();
        enrichFlowPlan.setDomainActions(List.of());
        enrichFlowPlan.setEnrichActions(List.of());

        Assertions.assertThatThrownBy(() -> enrichFlowPlanValidator.validate(enrichFlowPlan))
                .isInstanceOf(DeltafiConfigurationException.class)
                .hasMessage("Config named: enrichFlow had the following error: Enrich flow plans must contain one or more domain and/or enrich actions");
    }

    @Test
    void canLoadFlowPlan_hasErrors() {
        EnrichFlowPlan enrichFlowPlan = enrichFlowPlan();
        enrichFlowPlan.getEnrichActions().add(enrichAction("enrich2", "domain"));

        EnrichFlowPlan existingFlow = enrichFlowPlan("other");

        Mockito.when(enrichFlowPlanService.getAll()).thenReturn(List.of(existingFlow));

        Assertions.assertThatThrownBy(() -> enrichFlowPlanValidator.validate(enrichFlowPlan))
                .isInstanceOf(DeltafiConfigurationException.class)
                .hasMessage("" +
                        "Config named: enrichFlow had the following error: " +
                        "Action of type: org.deltafi.domain.Action is already configured in the enrich flow plan named: other with overlapping domain: domain; " +
                        "Config named: enrichFlow had the following error: " +
                        "Action of type: org.deltafi.enrich.Action is already configured in the enrich flow plan named: other with overlapping domain: domain; " +
                        "Config named: enrichFlow had the following error: " +
                        "Action of type: org.deltafi.enrich.Action is already configured in the enrich flow plan named: enrichFlow with overlapping domain: domain");
    }

    @Test
    void findDuplicateEnrichTypeInFlowPlan() {
        EnrichFlowPlan enrichFlow = enrichFlowPlan();
        enrichFlow.getEnrichActions().add(enrichAction("enrich2", "domain"));

        Assertions.assertThatThrownBy(() -> enrichFlowPlanValidator.validate(enrichFlow))
                        .isInstanceOf(DeltafiConfigurationException.class)
                        .hasMessage("Config named: enrichFlow had the following error: " +
                                "Action of type: org.deltafi.enrich.Action is already configured in the enrich flow plan named: enrichFlow with overlapping domain: domain");
    }

    @Test
    void noDuplicateEnrichTypeInFlowPlanDifferentDomains() {
        EnrichFlowPlan enrichFlow = enrichFlowPlan();
        enrichFlow.getEnrichActions().add(enrichAction("enrich2", "domain2"));

        // does not throw
        enrichFlowPlanValidator.validate(enrichFlow);
    }

    @Test
    void findDuplicateDomainTypeInFlowPlan() {
        EnrichFlowPlan enrichFlow = enrichFlowPlan();
        enrichFlow.getDomainActions().add(domainAction("domain2", "domain"));

        Assertions.assertThatThrownBy(() -> enrichFlowPlanValidator.validate(enrichFlow))
                .isInstanceOf(DeltafiConfigurationException.class)
                .hasMessage("Config named: enrichFlow had the following error: Action of type: org.deltafi.domain.Action is already configured in the enrich flow plan named: enrichFlow with overlapping domain: domain");
    }

    @Test
    void duplicateActionNameErrors() {
        EnrichFlowPlan enrichFlow = new EnrichFlowPlan("enrichFlow", null);

        DomainActionConfiguration domain = new DomainActionConfiguration("action", "org.deltafi.domain.Action1", null);
        enrichFlow.setDomainActions(List.of(domain));

        EnrichActionConfiguration enrich1 = new EnrichActionConfiguration("action", "org.deltafi.enrich.Action1", null);
        EnrichActionConfiguration enrich2 = new EnrichActionConfiguration("enrich", "org.deltafi.enrich.Action2", null);
        EnrichActionConfiguration enrich3 = new EnrichActionConfiguration("enrich", "org.deltafi.enrich.Action3", null);
        enrichFlow.setEnrichActions(List.of(enrich1, enrich2, enrich3));

        Assertions.assertThatThrownBy(() -> enrichFlowPlanValidator.validate(enrichFlow))
                .isInstanceOf(DeltafiConfigurationException.class)
                .hasMessage("Config named: action had the following error: The action name: action is duplicated for the following action types: org.deltafi.domain.Action1, org.deltafi.enrich.Action1; Config named: enrich had the following error: The action name: enrich is duplicated for the following action types: org.deltafi.enrich.Action2, org.deltafi.enrich.Action3");
    }

    EnrichFlowPlan enrichFlowPlan() {
        return enrichFlowPlan("enrichFlow");
    }

    EnrichFlowPlan enrichFlowPlan(String name) {
        EnrichFlowPlan enrichFlow = new EnrichFlowPlan(name, null);
        enrichFlow.setDomainActions(new ArrayList<>(List.of(domainAction("domain1", "domain"))));
        enrichFlow.setEnrichActions(new ArrayList<>(List.of(enrichAction("enrich1", "domain"))));
        return enrichFlow;
    }

    DomainActionConfiguration domainAction(String name, String domain) {
        return new DomainActionConfiguration(name, "org.deltafi.domain.Action", List.of(domain));
    }

    EnrichActionConfiguration enrichAction(String name, String domain) {
        return new EnrichActionConfiguration(name, "org.deltafi.enrich.Action", List.of(domain));
    }
}