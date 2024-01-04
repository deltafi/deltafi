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
import org.deltafi.common.types.Rule;
import org.deltafi.common.types.Subscriber;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

@Document
@Data
@EqualsAndHashCode(callSuper = true)
public class TransformFlow extends Flow implements Subscriber, Publisher {
    private List<TransformActionConfiguration> transformActions = new ArrayList<>();
    private int maxErrors = -1;
    private Set<Rule> subscriptions;
    private PublishRules publishRules;

    /**
     * Schema versions:
     * 0 - original
     * 1 - skipped
     * 2 - separate flow and action name
     */
    public static final int CURRENT_SCHEMA_VERSION = 2;
    private int schemaVersion;

    @Override
    public boolean migrate() {
        if (schemaVersion < 2) {
            transformActions.forEach(this::migrateAction);
        }

        if (schemaVersion < CURRENT_SCHEMA_VERSION) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
            return true;
        }

        return false;
    }

    @Override
    public ActionConfiguration findActionConfigByName(String actionNamed) {
        return actionNamed(transformActions, actionNamed);
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        return new ArrayList<>(transformActions);
    }

    @Override
    public List<DeltaFiConfiguration> findByConfigType(ConfigType configType)  {
        return switch (configType) {
            case TRANSFORM_FLOW -> List.of(asFlowConfiguration());
            case TRANSFORM_ACTION -> transformActions != null ? new ArrayList<>(transformActions) : Collections.emptyList();
            default -> Collections.emptyList();
        };
    }

    @Override
    public void updateActionNamesByFamily(EnumMap<ActionType, ActionFamily> actionFamilyMap) {
        updateActionNamesByFamily(actionFamilyMap, ActionType.TRANSFORM, actionNames(transformActions));
    }

    @Override
    public DeltaFiConfiguration asFlowConfiguration() {
        TransformFlowConfiguration transformFlowConfiguration = new TransformFlowConfiguration(name);
        transformFlowConfiguration.setTransformActions(transformActions.stream().map(ActionConfiguration::getName).toList());
        return transformFlowConfiguration;
    }

    @Override
    public Set<Rule> subscriptions() {
        return subscriptions;
    }

    @Override
    public PublishRules publishRules() {
        return publishRules;
    }
}
