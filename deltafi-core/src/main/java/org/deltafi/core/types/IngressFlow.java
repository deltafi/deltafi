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
package org.deltafi.core.types;


import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.ActionFamily;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;
import java.util.stream.Collectors;

@Document
@Data
@EqualsAndHashCode(callSuper = true)
public class IngressFlow extends Flow {
    private List<TransformActionConfiguration> transformActions = new ArrayList<>();
    private LoadActionConfiguration loadAction;

    @Override
    public ActionConfiguration findActionConfigByName(String actionNamed) {
        ActionConfiguration transformActionConfiguration = actionNamed(transformActions, actionNamed);
        if (transformActionConfiguration != null) {
            return transformActionConfiguration;
        }
        return nameMatches(loadAction, actionNamed) ? loadAction : null;
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>(transformActions);
        actionConfigurations.add(loadAction);
        return actionConfigurations;
    }

    @Override
    public List<DeltaFiConfiguration> findByConfigType(ConfigType configType)  {
        switch (configType) {
            case INGRESS_FLOW:
                return List.of(asFlowConfiguration());
            case TRANSFORM_ACTION:
                return Objects.nonNull(transformActions) ? new ArrayList<>(transformActions) : Collections.emptyList();
            case LOAD_ACTION:
                return List.of(loadAction);
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public void updateActionNamesByFamily(EnumMap<ActionType, ActionFamily> actionFamilyMap) {
        updateActionNamesByFamily(actionFamilyMap, ActionType.TRANSFORM, actionNames(transformActions));
        updateActionNamesByFamily(actionFamilyMap, ActionType.LOAD, loadAction.getName());
    }

    @Override
    public DeltaFiConfiguration asFlowConfiguration() {
        IngressFlowConfiguration ingressFlowConfiguration = new IngressFlowConfiguration(name, loadAction.getName());
        ingressFlowConfiguration.setTransformActions(transformActions.stream().map(ActionConfiguration::getName).collect(Collectors.toList()));
        return ingressFlowConfiguration;
    }
}
