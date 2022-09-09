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
import org.deltafi.core.domain.configuration.*;
import org.deltafi.core.domain.generated.types.ActionFamily;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;
import java.util.stream.Collectors;

@Data
@Document("ingressFlow")
@EqualsAndHashCode(callSuper = true)
public class IngressFlow extends Flow {
    private List<TransformActionConfiguration> transformActions = new ArrayList<>();
    private LoadActionConfiguration loadAction;

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>();
        actionConfigurations.add(loadAction);
        actionConfigurations.addAll(transformActions);
        return actionConfigurations;
    }

    @Override
    public List<DeltaFiConfiguration> findByConfigType(ConfigType configType)  {
        switch (configType) {
            case INGRESS_FLOW:
                return List.of(asFlowConfiguration());
            case LOAD_ACTION:
                return List.of(loadAction);
            case TRANSFORM_ACTION:
                return Objects.nonNull(transformActions) ? new ArrayList<>(transformActions) : Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public ActionConfiguration findActionConfigByName(String actionNamed) {
        if (nameMatches(loadAction, actionNamed)) {
            return loadAction;
        }

        return actionNamed(transformActions, actionNamed);
    }

    @Override
    public DeltaFiConfiguration asFlowConfiguration() {
        IngressFlowConfiguration ingressFlowConfiguration = new IngressFlowConfiguration();
        ingressFlowConfiguration.setName(this.getName());
        ingressFlowConfiguration.setLoadAction(this.loadAction.getName());
        ingressFlowConfiguration.setTransformActions(this.transformActions.stream().map(ActionConfiguration::getName).collect(Collectors.toList()));
        return ingressFlowConfiguration;
    }

    @Override
    public void updateActionNamesByFamily(EnumMap<ActionType, ActionFamily> actionFamilyMap) {
        updateActionNamesByFamily(actionFamilyMap, ActionType.LOAD, loadAction.getName());
        updateActionNamesByFamily(actionFamilyMap, ActionType.TRANSFORM, actionNames(transformActions));
    }

}
