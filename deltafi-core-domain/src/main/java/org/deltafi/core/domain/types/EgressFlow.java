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
import org.deltafi.core.domain.api.types.ActionType;
import org.deltafi.core.domain.api.types.ConfigType;
import org.deltafi.core.domain.configuration.*;
import org.deltafi.core.domain.generated.types.ActionFamily;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

@Data
@Document("egressFlow")
@EqualsAndHashCode(callSuper = true)
public class EgressFlow extends Flow {

    private EgressActionConfiguration egressAction;
    private FormatActionConfiguration formatAction;
    private List<ValidateActionConfiguration> validateActions = new ArrayList<>();
    private List<String> includeIngressFlows;
    private List<String> excludeIngressFlows;

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
        switch (configType) {
            case EGRESS_FLOW:
                return List.of(asFlowConfiguration());
            case FORMAT_ACTION:
                return List.of(formatAction);
            case VALIDATE_ACTION:
                return Objects.nonNull(validateActions) ? new ArrayList<>(validateActions) : Collections.emptyList();
            case EGRESS_ACTION:
                return List.of(egressAction);
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public ActionConfiguration findActionConfigByName(String actionName) {
        if (nameMatches(egressAction, actionName)) {
            return egressAction;
        }

        if (nameMatches(formatAction, actionName)) {
            return formatAction;
        }

        return actionNamed(validateActions, actionName);
    }

    public List<String> validateActionNames() {
        return actionNames(validateActions);
    }

    public void updateActionNamesByFamily(EnumMap<ActionType, ActionFamily> actionFamilyMap) {
        updateActionNamesByFamily(actionFamilyMap, ActionType.FORMAT, formatAction.getName());
        updateActionNamesByFamily(actionFamilyMap, ActionType.VALIDATE, validateActionNames());
        updateActionNamesByFamily(actionFamilyMap, ActionType.EGRESS, egressAction.getName());
    }

    @Override
    public DeltaFiConfiguration asFlowConfiguration() {
        EgressFlowConfiguration egressFlowConfiguration = new EgressFlowConfiguration();
        egressFlowConfiguration.setName(getName());
        egressFlowConfiguration.setFormatAction(this.formatAction.getName());
        egressFlowConfiguration.setValidateActions(validateActionNames());
        egressFlowConfiguration.setEgressAction(this.egressAction.getName());
        egressFlowConfiguration.setIncludeIngressFlows(this.includeIngressFlows);
        egressFlowConfiguration.setExcludeIngressFlows(this.excludeIngressFlows);

        return egressFlowConfiguration;
    }

    public boolean flowMatches(String flow) {
        return includesFlow(flow) && notExcludedFlow(flow);
    }

    private boolean includesFlow(String flow) {
        return null == getIncludeIngressFlows() || getIncludeIngressFlows().contains(flow);
    }

    private boolean notExcludedFlow(String flow) {
        return nullOrEmpty(getExcludeIngressFlows()) || !getExcludeIngressFlows().contains(flow);
    }

    private boolean nullOrEmpty(List<String> list) {
        return Objects.isNull(list) || list.isEmpty();
    }

}
