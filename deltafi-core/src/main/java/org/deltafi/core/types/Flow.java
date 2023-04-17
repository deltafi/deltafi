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
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.common.types.DeltaFiConfiguration;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.core.generated.types.ActionFamily;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.springframework.data.annotation.Id;

import java.util.*;

@Data
public abstract class Flow {

    @Id
    protected String name;
    protected String description;
    protected PluginCoordinates sourcePlugin;
    protected FlowStatus flowStatus = new FlowStatus(FlowState.STOPPED, new ArrayList<>(), false);
    // list of variables that are applicable to this flow
    protected Set<Variable> variables = new HashSet<>();

    /**
     * Run migrations needed to upgrade to latest version
     * This method should be overridden by child classes that support migrations
     * @return boolean indicating whether a migration occurred
     */
    public boolean migrate() {
        return false;
    }

    /**
     * Get all the configurations in this flow, including itself
     * @return all configurations in the flow
     */
    public List<DeltaFiConfiguration> allConfigurations() {
        List<DeltaFiConfiguration> configs = new ArrayList<>(allActionConfigurations());
        configs.add(asFlowConfiguration());
        return configs;
    }

    /**
     * Add the given action names to an existing ActionFamily or create a
     * new ActionFamily to hold the list of action names for that family
     * @param actionFamilyMap map of family name to list of action names
     * @param actionType type of action i.e. load, transform ... egress
     * @param actionNames list of action names to add
     */
    public void updateActionNamesByFamily(EnumMap<ActionType, ActionFamily> actionFamilyMap, ActionType actionType, List<String> actionNames) {
        if (actionFamilyMap.containsKey(actionType)) {
            actionFamilyMap.get(actionType).getActionNames().addAll(actionNames);
        } else {
            ActionFamily newFamily = ActionFamily.newBuilder().family(actionType.name()).actionNames(new ArrayList<>(actionNames)).build();
            actionFamilyMap.put(actionType, newFamily);
        }
    }

    /**
     * Find the action configuration with the given name in this flow
     * @param actionName name of the action configuration to find
     * @return ActionConfiguration if it exists otherwise null
     */
    public abstract ActionConfiguration findActionConfigByName(String actionName);

    /**
     * Get all the action configurations in this flow
     * @return all action configurations in the flow
     */
    public abstract List<ActionConfiguration> allActionConfigurations();

    /**
     * Get all the action configurations of the given type in this flow
     * @param configType type of ActionsConfigurations that should be returned
     * @return list of matching ActionConfigurations
     */
    public abstract List<DeltaFiConfiguration> findByConfigType(ConfigType configType);

    /**
     * Add the action names in this flow to the appropriate action family
     * @param actionFamilyMap map of family type to action families
     */
    public abstract void updateActionNamesByFamily(EnumMap<ActionType, ActionFamily> actionFamilyMap);

    public abstract DeltaFiConfiguration asFlowConfiguration();

    /**
     * Add the given action name to an existing ActionFamily or create a
     * new ActionFamily to hold the list of action names for that family
     * @param actionFamilyMap map of family name to list of action names
     * @param actionType type of action i.e. load, transform ... egress
     * @param actionName action name to add
     */
    public void updateActionNamesByFamily(EnumMap<ActionType, ActionFamily> actionFamilyMap, ActionType actionType, String actionName) {
        updateActionNamesByFamily(actionFamilyMap, actionType, List.of(actionName));
    }

    public boolean isRunning() {
        return FlowState.RUNNING.equals(getFlowStatus().getState());
    }

    public boolean isStopped() {
        return FlowState.STOPPED.equals(getFlowStatus().getState());
    }

    public boolean isInvalid() {
        return FlowState.INVALID.equals(getFlowStatus().getState());
    }

    public boolean isTestMode() {
        return flowStatus.getTestMode();
    }

    public void setTestMode( boolean testMode ) { flowStatus.setTestMode(testMode); }

    public boolean hasErrors() {
        return !getFlowStatus().getErrors().isEmpty();
    }

    public <T extends ActionConfiguration> List<String> actionNames(List<T> actions) {
        return Objects.isNull(actions) ? List.of() : actions.stream().map(ActionConfiguration::getName).toList();
    }

    public <T extends ActionConfiguration> ActionConfiguration actionNamed(List<T> actions, String actionName) {
        return actions.stream()
                .filter(actionConfiguration -> nameMatches(actionConfiguration, actionName))
                .findFirst().orElse(null);
    }

    public boolean nameMatches(ActionConfiguration action, String named) {
        return named.equals(action.getName());
    }
}
