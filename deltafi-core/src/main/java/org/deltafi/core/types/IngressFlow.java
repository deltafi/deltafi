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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class IngressFlow extends Flow {
    private List<TransformActionConfiguration> transformActions = new ArrayList<>();
    private LoadActionConfiguration loadAction;
    private int maxErrors = -1;
    private int schemaVersion;

    /**
     * Schema versions:
     * 0 - original
     * 1 - change default/unlimited number of maxErrors from 0 to -1
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    @Override
    public boolean migrate() {
        if (schemaVersion < 1 && maxErrors == 0) {
            maxErrors = -1;
        }

        if (schemaVersion < CURRENT_SCHEMA_VERSION) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
            return true;
        }

        return false;
    }

    @Override
    public ActionConfiguration findActionConfigByName(String actionNamed) {
        ActionConfiguration transformActionConfiguration = actionNamed(transformActions, actionNamed);
        if (transformActionConfiguration != null) {
            return transformActionConfiguration;
        }

        if (loadAction != null && nameMatches(loadAction, actionNamed)) {
            return loadAction;
        }

        return null;
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>(transformActions);
        if (loadAction != null) {
            actionConfigurations.add(loadAction);
        }
        return actionConfigurations;
    }

    @Override
    public List<DeltaFiConfiguration> findByConfigType(ConfigType configType)  {
        return switch (configType) {
            case INGRESS_FLOW -> List.of(asFlowConfiguration());
            case TRANSFORM_ACTION -> transformActions != null ? new ArrayList<>(transformActions) : Collections.emptyList();
            case LOAD_ACTION -> loadAction != null ? List.of(loadAction) : Collections.emptyList();
            default -> Collections.emptyList();
        };
    }

    @Override
    public void updateActionNamesByFamily(EnumMap<ActionType, ActionFamily> actionFamilyMap) {
        updateActionNamesByFamily(actionFamilyMap, ActionType.TRANSFORM, actionNames(transformActions));
        if (loadAction != null) {
            updateActionNamesByFamily(actionFamilyMap, ActionType.LOAD, loadAction.getName());
        }
    }

    @Override
    public DeltaFiConfiguration asFlowConfiguration() {
        IngressFlowConfiguration ingressFlowConfiguration = new IngressFlowConfiguration(name);
        ingressFlowConfiguration.setTransformActions(transformActions.stream().map(ActionConfiguration::getName).toList());
        if (loadAction != null) {
            ingressFlowConfiguration.setLoadAction(loadAction.getName());
        }
        return ingressFlowConfiguration;
    }
}
