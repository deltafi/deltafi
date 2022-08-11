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
package org.deltafi.core.domain.types;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.common.types.ActionType;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.configuration.DeltaFiConfiguration;
import org.deltafi.core.domain.configuration.EnrichActionConfiguration;
import org.deltafi.core.domain.configuration.EnrichFlowConfiguration;
import org.deltafi.core.domain.generated.types.ActionFamily;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

@Data
@Document("enrichFlow")
@EqualsAndHashCode(callSuper = true)
public class EnrichFlow extends Flow {

    private List<EnrichActionConfiguration> enrichActions;

    @Override
    public ActionConfiguration findActionConfigByName(String actionName) {
        return actionNamed(enrichActions, actionName);
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        return new ArrayList<>(enrichActions);
    }

    @Override
    public List<DeltaFiConfiguration> findByConfigType(ConfigType configType) {
        switch (configType) {
            case ENRICH_FLOW:
                return List.of(asFlowConfiguration());
            case ENRICH_ACTION:
                return null != enrichActions ? new ArrayList<>(enrichActions) : Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public void updateActionNamesByFamily(EnumMap<ActionType, ActionFamily> actionFamilyMap) {
        updateActionNamesByFamily(actionFamilyMap, ActionType.ENRICH, actionNames(enrichActions));
    }

    @Override
    DeltaFiConfiguration asFlowConfiguration() {
        EnrichFlowConfiguration enrichFlowConfiguration = new EnrichFlowConfiguration();
        enrichFlowConfiguration.setName(getName());
        enrichFlowConfiguration.setEnrichActions(actionNames(enrichActions));
        return enrichFlowConfiguration;
    }
}
