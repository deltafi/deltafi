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
package org.deltafi.core.domain.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.domain.api.types.ConfigType;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.configuration.DeltaFiConfiguration;
import org.deltafi.core.domain.generated.types.ConfigQueryInput;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.generated.types.FlowStatus;
import org.deltafi.core.domain.types.Flow;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class FlowControllerService<T extends Flow> {

    protected Map<String, T> flowCache;

    /**
     * Find the flow with the given name.
     *
     * Throws an exception if the given flow does not exist
     * @param flowName name of the flow to find
     * @return the flow with the given name
     */
    public abstract T getFlowOrThrow(String flowName);

    /**
     * Get all flows in the system
     *
     * @return all flows
     */
    abstract List<T> getAll();

    /**
     * Get all flows in a running state
     * @return all running flows
     */
    abstract List<T> getRunningFlows();

    /**
     * Update the state of the given flow to the passed in state.
     *
     * @param flowName name of the flow that should be updated
     * @param flowState new state for the flow
     * @return true if the flow state was changed
     */
    abstract boolean updateFlowState(String flowName, FlowState flowState);

    /**
     * Get the flow type (i.e. ingress or egress)
     * @return type of flow
     */
    abstract String flowType();


    @PostConstruct
    public void refreshCache() {
        flowCache = getRunningFlows()
                .stream().collect(Collectors.toMap(Flow::getName, Function.identity()));
    }

    public boolean startFlow(String flowName) {
        T flow = getFlowOrThrow(flowName);

        FlowStatus flowStatus = flow.getFlowStatus();

        if (FlowState.INVALID.equals(flowStatus.getState())) {
            log.warn("Tried to start " + flowType()+ " flow: " + flowName + " when it was in an invalid state");
            throw new IllegalStateException("Flow: " + flowName + " cannot be started until configuration errors are resolved");
        }

        if (FlowState.RUNNING.equals(flowStatus.getState())) {
            log.warn("Tried to start " + flowType() + " flow: " + flowName + " when it was already running");
            return false;
        }

        return updateAndRefresh(flowName, FlowState.RUNNING);
    }

    public boolean stopFlow(String flowName) {
        T ingressFlow = getFlowOrThrow(flowName);

        if (!FlowState.RUNNING.equals(ingressFlow.getFlowStatus().getState())) {
            log.warn("Tried to stop " + flowType()+ " flow " + flowName + " which was not running");
            return false;
        }

        return updateAndRefresh(flowName, FlowState.STOPPED);
    }

    public ActionConfiguration findActionConfig(String flowName, String actionName) {
        ActionConfiguration maybeFound = doFindActionConfig(flowName, actionName);

        if (Objects.isNull(maybeFound)) {
            refreshCache();
            maybeFound = doFindActionConfig(flowName, actionName);
        }

        return maybeFound;
    }

    ActionConfiguration doFindActionConfig(String flowName, String actionName) {
        T flow = flowCache.get(flowName);

        if (Objects.isNull(flow)) {
            return null;
        }

        return flow.findActionConfigByName(actionName);
    }

    public T getRunningFlowByName(String flowName) {
        if (!flowCache.containsKey(flowName)) {
            refreshCache();
        }

        if (!flowCache.containsKey(flowName)) {
            throw new DgsEntityNotFoundException("Flow of type " + flowType() + " named " + flowName + " is not running");
        }

        return flowCache.get(flowName);
    }

    public Map<PluginCoordinates, List<T>> getFlowsGroupedByPlugin() {
        return getAll().stream()
                .collect(Collectors.groupingBy(T::getSourcePlugin));
    }

    public List<DeltaFiConfiguration> getConfigs(ConfigQueryInput actionQueryInput) {
        return Objects.nonNull(actionQueryInput) ? findConfigsWithFilter(actionQueryInput) : findAllConfigs();
    }

    private List<DeltaFiConfiguration> findConfigsWithFilter(ConfigQueryInput actionQueryInput) {
        ConfigType configType = ConfigType.valueOf(actionQueryInput.getConfigType().name());
        String nameFilter = actionQueryInput.getName();
        List<DeltaFiConfiguration> allByType = allOfConfigType(configType);
        return Objects.isNull(nameFilter) ? allByType :
                allByType.stream().filter(actionConfig -> actionQueryInput.getName().equals(actionConfig.getName())).collect(Collectors.toList());
    }

    private List<DeltaFiConfiguration> allOfConfigType(ConfigType configType) {
        return getAll().stream()
                .map(flow -> flow.findByConfigType(configType))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<DeltaFiConfiguration> findAllConfigs() {
        return getAll().stream()
                .map(Flow::allConfigurations)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private boolean updateAndRefresh(String flowName, FlowState flowState) {
        if (updateFlowState(flowName, flowState)) {
            refreshCache();
            return true;
        }

        return false;
    }

}
