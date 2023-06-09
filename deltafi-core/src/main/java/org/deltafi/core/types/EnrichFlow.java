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
package org.deltafi.core.types;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.ActionFamily;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

@Document
@Data
@EqualsAndHashCode(callSuper = true)
public class EnrichFlow extends Flow {
    private List<DomainActionConfiguration> domainActions = new ArrayList<>();
    private List<EnrichActionConfiguration> enrichActions = new ArrayList<>();

    @Override
    public ActionConfiguration findActionConfigByName(String actionName) {
        ActionConfiguration domainActionConfiguration = actionNamed(domainActions, actionName);
        if (domainActionConfiguration != null) {
            return domainActionConfiguration;
        }
        return actionNamed(enrichActions, actionName);
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>(domainActions);
        actionConfigurations.addAll(enrichActions);
        return actionConfigurations;
    }

    @Override
    public List<DeltaFiConfiguration> findByConfigType(ConfigType configType) {
        return switch (configType) {
            case ENRICH_FLOW -> List.of(asFlowConfiguration());
            case DOMAIN_ACTION -> new ArrayList<>(domainActions);
            case ENRICH_ACTION -> new ArrayList<>(enrichActions);
            default -> Collections.emptyList();
        };
    }

    @Override
    public void updateActionNamesByFamily(EnumMap<ActionType, ActionFamily> actionFamilyMap) {
        updateActionNamesByFamily(actionFamilyMap, ActionType.DOMAIN, actionNames(domainActions));
        updateActionNamesByFamily(actionFamilyMap, ActionType.ENRICH, actionNames(enrichActions));
    }

    @Override
    public DeltaFiConfiguration asFlowConfiguration() {
        EnrichFlowConfiguration enrichFlowConfiguration = new EnrichFlowConfiguration(name);
        enrichFlowConfiguration.setDomainActions(actionNames(domainActions));
        enrichFlowConfiguration.setEnrichActions(actionNames(enrichActions));
        return enrichFlowConfiguration;
    }
}
