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

import java.util.*;

@Document
@Data
@EqualsAndHashCode(callSuper = true)
public class EgressFlow extends Flow {
    private List<String> includeNormalizeFlows;
    private List<String> excludeNormalizeFlows;

    private FormatActionConfiguration formatAction;
    private List<ValidateActionConfiguration> validateActions = new ArrayList<>();
    private EgressActionConfiguration egressAction;
    private Set<String> expectedAnnotations;

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
            migrateAction(formatAction);
            validateActions.forEach(this::migrateAction);
            migrateAction(egressAction);
        }

        if (schemaVersion < CURRENT_SCHEMA_VERSION) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
            return true;
        }

        return false;
    }

    @Override
    public ActionConfiguration findActionConfigByName(String actionName) {
        if (nameMatches(formatAction, actionName)) {
            return formatAction;
        }

        ActionConfiguration validateActionConfiguration = actionNamed(validateActions, actionName);
        if (validateActionConfiguration != null) {
            return validateActionConfiguration;
        }

        return nameMatches(egressAction, actionName) ? egressAction : null;
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>();
        actionConfigurations.add(formatAction);
        actionConfigurations.add(egressAction);
        actionConfigurations.addAll(validateActions);
        return actionConfigurations;
    }

    @Override
    public List<DeltaFiConfiguration> findByConfigType(ConfigType configType) {
        return switch (configType) {
            case EGRESS_FLOW -> List.of(asFlowConfiguration());
            case FORMAT_ACTION -> List.of(formatAction);
            case VALIDATE_ACTION ->
                    Objects.nonNull(validateActions) ? new ArrayList<>(validateActions) : Collections.emptyList();
            case EGRESS_ACTION -> List.of(egressAction);
            default -> Collections.emptyList();
        };
    }

    @Override
    public void updateActionNamesByFamily(EnumMap<ActionType, ActionFamily> actionFamilyMap) {
        updateActionNamesByFamily(actionFamilyMap, ActionType.FORMAT, formatAction.getName());
        updateActionNamesByFamily(actionFamilyMap, ActionType.VALIDATE, validateActionNames());
        updateActionNamesByFamily(actionFamilyMap, ActionType.EGRESS, egressAction.getName());
    }

    @Override
    public DeltaFiConfiguration asFlowConfiguration() {
        EgressFlowConfiguration egressFlowConfiguration = new EgressFlowConfiguration(name, formatAction.getName(), egressAction.getName());
        egressFlowConfiguration.setIncludeIngressFlows(includeNormalizeFlows);
        egressFlowConfiguration.setExcludeIngressFlows(excludeNormalizeFlows);
        egressFlowConfiguration.setValidateActions(validateActionNames());

        return egressFlowConfiguration;
    }

    public List<String> validateActionNames() {
        return actionNames(validateActions);
    }

    public boolean flowMatches(String flow) {
        return includesFlow(flow) && notExcludedFlow(flow);
    }

    private boolean includesFlow(String flow) {
        return null == getIncludeNormalizeFlows() || getIncludeNormalizeFlows().contains(flow);
    }

    private boolean notExcludedFlow(String flow) {
        return nullOrEmpty(getExcludeNormalizeFlows()) || !getExcludeNormalizeFlows().contains(flow);
    }

    private boolean nullOrEmpty(List<String> list) {
        return Objects.isNull(list) || list.isEmpty();
    }

}
