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
import org.deltafi.core.domain.api.types.ConfigType;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.configuration.*;
import org.deltafi.core.domain.generated.types.ActionFamily;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.generated.types.FlowStatus;
import org.deltafi.core.domain.generated.types.Variable;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;
import java.util.stream.Collectors;

@Data
@Document("ingressFlow")
public class IngressFlow implements Flow {

    @Id
    private String name;
    private String description;
    private PluginCoordinates sourcePlugin;
    private FlowStatus flowStatus = new FlowStatus(FlowState.STOPPED, new ArrayList<>());
    private String type;
    private List<TransformActionConfiguration> transformActions = new ArrayList<>();
    private LoadActionConfiguration loadAction;
    // list of variables that are applicable to this flow
    private Set<Variable> variables = new HashSet<>();

    @Override
    public List<DeltaFiConfiguration> allConfigurations() {
        List<DeltaFiConfiguration> allConfigurations = new ArrayList<>();
        allConfigurations.add(loadAction);
        allConfigurations.addAll(transformActions);
        allConfigurations.add(asIngressFlowConfiguration());
        return allConfigurations;
    }

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
                return List.of(asIngressFlowConfiguration());
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

        return transformAction(actionNamed);
    }

    public DeltaFiConfiguration asIngressFlowConfiguration() {
        IngressFlowConfiguration ingressFlowConfiguration = new IngressFlowConfiguration();
        ingressFlowConfiguration.setName(this.name);
        ingressFlowConfiguration.setType(this.type);
        ingressFlowConfiguration.setLoadAction(this.loadAction.getName());
        ingressFlowConfiguration.setTransformActions(this.transformActions.stream().map(ActionConfiguration::getName).collect(Collectors.toList()));
        return ingressFlowConfiguration;
    }

    @Override
    public void updateActionNamesByFamily(Map<String, ActionFamily> actionFamilyMap) {
        updateActionNamesByFamily(actionFamilyMap, "load", loadAction.getName());
        updateActionNamesByFamily(actionFamilyMap, "transform", transformActionNames());
    }

    public boolean isValid() {
        return !FlowState.INVALID.equals(flowStatus.getState());
    }

    public boolean isRunning() {
        return FlowState.RUNNING.equals(flowStatus.getState());
    }

    private List<String> transformActionNames() {
        return transformActions.stream().map(TransformActionConfiguration::getName).collect(Collectors.toList());
    }

    private TransformActionConfiguration transformAction(String named) {
        return transformActions.stream()
                .filter(transform -> nameMatches(transform, named))
                .findFirst().orElse(null);
    }

    private boolean nameMatches(DeltaFiConfiguration action, String named) {
        return named.equals(action.getName());
    }


}
