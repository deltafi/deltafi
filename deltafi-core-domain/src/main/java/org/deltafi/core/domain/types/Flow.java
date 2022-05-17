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

import org.deltafi.core.domain.api.types.ConfigType;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.configuration.DeltaFiConfiguration;
import org.deltafi.core.domain.generated.types.ActionFamily;
import org.deltafi.core.domain.generated.types.FlowStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface Flow {

    /**
     * Get the PluginCoordinates of the plugin that provided this flow
     * @return source plugin coordinates
     */
    PluginCoordinates getSourcePlugin();

    /**
     * Get the name of the flow
     * @return name of the flow
     */
    String getName();

    /**
     * Get the status of the flow
     * @return flow status
     */
    FlowStatus getFlowStatus();

    /**
     * Find the action configuration with the given name in this flow
     * @param actionName name of the action configuration to find
     * @return ActionConfiguration if it exists otherwise null
     */
    ActionConfiguration findActionConfigByName(String actionName);

    /**
     * Get all the configurations in this flow, including itself
     * @return all configurations in the flow
     */
    List<DeltaFiConfiguration> allConfigurations();

    /**
     * Get all the action configurations in this flow
     * @return all action configurations in the flow
     */
    List<ActionConfiguration> allActionConfigurations();

    /**
     * Get all the action configurations of the given type in this flow
     * @param configType type of ActionsConfigurations that should be returned
     * @return list of matching ActionConfigurations
     */
    List<DeltaFiConfiguration> findByConfigType(ConfigType configType);

    /**
     * Add the action names in this flow to the appropriate action family
     * @param actionFamilyMap map of action names to ActionFamily
     */
    void updateActionNamesByFamily(Map<String, ActionFamily> actionFamilyMap);

    /**
     * Add the given action name to an existing ActionFamily or create a
     * new ActionFamily to hold the list of action names for that family
     * @param actionFamilyMap map of family name to list of action names
     * @param actionFamily type of action i.e. load, transform ... egress
     * @param actionName action name to add
     */
    default void updateActionNamesByFamily(Map<String, ActionFamily> actionFamilyMap, String actionFamily, String actionName) {
        updateActionNamesByFamily(actionFamilyMap, actionFamily, List.of(actionName));
    }

    /**
     * Add the given action names to an existing ActionFamily or create a
     * new ActionFamily to hold the list of action names for that family
     * @param actionFamilyMap map of family name to list of action names
     * @param actionFamily type of action i.e. load, transform ... egress
     * @param actionNames list of action names to add
     */
    default void updateActionNamesByFamily(Map<String, ActionFamily> actionFamilyMap, String actionFamily, List<String> actionNames) {
        if (actionFamilyMap.containsKey(actionFamily)) {
            actionFamilyMap.get(actionFamily).getActionNames().addAll(actionNames);
        } else {
            ActionFamily newFamily = ActionFamily.newBuilder().family(actionFamily).actionNames(new ArrayList<>(actionNames)).build();
            actionFamilyMap.put(actionFamily, newFamily);
        }
    }

}
