/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.uuid.Generators;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.ActionFamily;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.hibernate.annotations.Type;

import java.util.*;

@Entity
@Table(name = "flows", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"name", "type"} )
})
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Data
@NoArgsConstructor
public abstract class Flow {
    @Id
    UUID id = Generators.timeBasedEpochGenerator().generate();

    private String name;

    @Enumerated(EnumType.STRING)
    private FlowType type;

    @Column(length = 100_000)
    protected String description;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    protected PluginCoordinates sourcePlugin;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    protected FlowStatus flowStatus = new FlowStatus(FlowState.STOPPED, new ArrayList<>(), false, true);

    // list of variables that are applicable to this dataSource
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    protected Set<Variable> variables = new HashSet<>();

    public Flow(String name, FlowType type, String description, PluginCoordinates sourcePlugin) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.sourcePlugin = sourcePlugin;
    }

    /**
     * Add the given action names to an existing ActionFamily or create a
     * new ActionFamily to hold the list of action names for that family
     * @param actionFamilyMap map of family name to list of action names
     * @param actionType type of action i.e. load, transform ... egress
     * @param actionNames list of action names to add
     */
    public static void updateActionNamesByFamily(Map<ActionType, ActionFamily> actionFamilyMap, ActionType actionType, List<String> actionNames) {
        if (actionFamilyMap.containsKey(actionType)) {
            actionFamilyMap.get(actionType).getActionNames().addAll(actionNames);
        } else {
            ActionFamily newFamily = ActionFamily.newBuilder().family(actionType.name()).actionNames(new ArrayList<>(actionNames)).build();
            actionFamilyMap.put(actionType, newFamily);
        }
    }

    /**
     * Find the action configuration with the given name in this dataSource
     * @param actionName name of the action configuration to find
     * @return ActionConfiguration if it exists otherwise null
     */
    public abstract ActionConfiguration findActionConfigByName(String actionName);

    /**
     * Get all the action configurations in this dataSource
     * @return all action configurations in the dataSource
     */
    public abstract List<ActionConfiguration> allActionConfigurations();

    /**
     * Add the given action name to an existing ActionFamily or create a
     * new ActionFamily to hold the list of action names for that family
     * @param actionFamilyMap map of family name to list of action names
     * @param actionType type of action i.e. load, transform ... egress
     * @param actionName action name to add
     */
    public void updateActionNamesByFamily(Map<ActionType, ActionFamily> actionFamilyMap, ActionType actionType, String actionName) {
        updateActionNamesByFamily(actionFamilyMap, actionType, List.of(actionName));
    }

    public boolean isRunning() {
        return FlowState.RUNNING.equals(getFlowStatus().getState());
    }

    public boolean isStopped() {
        return FlowState.STOPPED.equals(getFlowStatus().getState());
    }

    public boolean isPaused() {
        return FlowState.PAUSED.equals(getFlowStatus().getState());
    }

    public boolean isInvalid() {
        return !getFlowStatus().getValid();
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
        return named != null && named.equals(action.getName());
    }

    /**
     * Copy state fields from the source dataSource into the current dataSource
     * These should be fields that do not come from the FlowPlan
     * @param sourceFlow to copy state from
     */
    public void copyFlowSpecificState(Flow sourceFlow) {
    }
}
