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
package org.deltafi.core.services;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.NormalizeFlowPlan;
import org.deltafi.core.converters.NormalizeFlowPlanConverter;
import org.deltafi.core.generated.types.IngressFlowErrorState;
import org.deltafi.core.plugin.SystemPluginService;
import org.deltafi.core.repo.NormalizeFlowRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.NormalizeFlowSnapshot;
import org.deltafi.core.types.NormalizeFlow;
import org.deltafi.core.types.Result;
import org.deltafi.core.validation.NormalizeFlowValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NormalizeFlowService extends FlowService<NormalizeFlowPlan, NormalizeFlow, NormalizeFlowSnapshot> {

    private static final NormalizeFlowPlanConverter NORMALIZE_FLOW_PLAN_CONVERTER = new NormalizeFlowPlanConverter();

    private final ErrorCountService errorCountService;

    public NormalizeFlowService(NormalizeFlowRepo normalizeFlowRepo, PluginVariableService pluginVariableService, NormalizeFlowValidator normalizeFlowValidator, ErrorCountService errorCountService, BuildProperties buildProperties) {
        super("normalize", normalizeFlowRepo, pluginVariableService, NORMALIZE_FLOW_PLAN_CONVERTER, normalizeFlowValidator, buildProperties);

        this.errorCountService = errorCountService;
    }

    @Override
    void copyFlowSpecificFields(NormalizeFlow sourceFlow, NormalizeFlow targetFlow) {
        targetFlow.setMaxErrors(sourceFlow.getMaxErrors());
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        refreshCache();
        systemSnapshot.setNormalizeFlows(getAll().stream().map(NormalizeFlowSnapshot::new).toList());
    }

    @Override
    public List<NormalizeFlowSnapshot> getFlowSnapshots(SystemSnapshot systemSnapshot) {
        return systemSnapshot.getNormalizeFlows();
    }

    @Override
    public boolean flowSpecificUpdateFromSnapshot(NormalizeFlow flow, NormalizeFlowSnapshot normalizeFlowSnapshot, Result result) {
        if (flow.getMaxErrors() != normalizeFlowSnapshot.getMaxErrors()) {
            flow.setMaxErrors(normalizeFlowSnapshot.getMaxErrors());
            return true;
        }

        return false;
    }

    /**
     * Sets the maximum number of errors allowed for a given flow, identified by its name.
     * If the maximum errors for the flow are already set to the specified value, the method
     * logs a warning and returns false. If the update is successful, the method refreshes the
     * cache and returns true.
     *
     * @param flowName  The name of the flow to update, represented as a {@code String}.
     * @param maxErrors The new maximum number of errors to be set for the specified flow, as an {@code int}.
     * @return A {@code boolean} value indicating whether the update was successful (true) or not (false).
     */
    public boolean setMaxErrors(String flowName, int maxErrors) {
        NormalizeFlow flow = getFlowOrThrow(flowName);

        if (flow.getMaxErrors() == maxErrors) {
            log.warn("Tried to set max errors on normalize flow {} to {} when already set", flowName, maxErrors);
            return false;
        }

        if (((NormalizeFlowRepo) flowRepo).updateMaxErrors(flowName, maxErrors)) {
            refreshCache();
            return true;
        }

        return false;
    }

    /**
     * Retrieves a map containing the maximum number of errors allowed per flow.
     * This method filters out flows with a maximum error count of 0, only including
     * those with a positive maximum error count.
     *
     * @return A {@code Map<String, Integer>} where each key represents a flow name,
     * and the corresponding value is the maximum number of errors allowed for that flow.
     */
    public Map<String, Integer> maxErrorsPerFlow() {
        return getRunningFlows().stream()
                .filter(e -> e.getMaxErrors() >= 0)
                .collect(Collectors.toMap(NormalizeFlow::getName, NormalizeFlow::getMaxErrors));
    }

    public List<IngressFlowErrorState> ingressFlowErrorsExceeded() {
        return getRunningFlows().stream()
                .map(f -> new IngressFlowErrorState(f.getName(), errorCountService.errorsForFlow(f.getName()), f.getMaxErrors()))
                .filter(s -> s.getMaxErrors() >= 0 && s.getCurrErrors() > s.getMaxErrors())
                .toList();
    }

    public Set<String> flowErrorsExceeded() {
        return ingressFlowErrorsExceeded()
                .stream()
                .map(IngressFlowErrorState::getName)
                .collect(Collectors.toSet());
    }

}
