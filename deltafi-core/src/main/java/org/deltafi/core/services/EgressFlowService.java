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
package org.deltafi.core.services;

import org.deltafi.core.converters.EgressFlowPlanConverter;
import org.deltafi.core.exceptions.DeltafiConfigurationException;
import org.deltafi.core.repo.EgressFlowRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.common.types.EgressFlowPlan;
import org.deltafi.core.validation.EgressFlowValidator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EgressFlowService extends FlowService<EgressFlowPlan, EgressFlow> {

    private static final EgressFlowPlanConverter EGRESS_FLOW_PLAN_CONVERTER = new EgressFlowPlanConverter();

    public EgressFlowService(EgressFlowRepo flowRepo, PluginVariableService pluginVariableService, EgressFlowValidator egressFlowValidator) {
        super("egress", flowRepo, pluginVariableService, EGRESS_FLOW_PLAN_CONVERTER, egressFlowValidator);
    }

    public List<EgressFlow> getMatchingFlows(String ingressFlow) {
        List<EgressFlow> flows = findMatchingFlows(ingressFlow);

        if (flows.isEmpty()) {
            refreshCache();
            flows = findMatchingFlows(ingressFlow);
        }

        return flows;
    }

    List<EgressFlow> findMatchingFlows(String ingressFlow) {
        return flowCache.values().stream()
                .filter(egressFlow -> egressFlow.flowMatches(ingressFlow))
                .toList();
    }

    public EgressFlow withFormatActionNamed(String formatActionName) {
        EgressFlow egressFlow = getRunningFlowByName(getFlowName(formatActionName));

        if (!formatActionName.equals(egressFlow.getFormatAction().getName())) {
            throw new DeltafiConfigurationException("Egress flow " + egressFlow + " no longer contains a format action with the name " + formatActionName);
        }

        return egressFlow;
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        systemSnapshot.setRunningEgressFlows(getRunningFlowNames());
        systemSnapshot.setTestEgressFlows(getTestFlowNames());
    }

    @Override
    List<String> getRunningFromSnapshot(SystemSnapshot systemSnapshot) {
        return systemSnapshot.getRunningEgressFlows();
    }

    @Override
    List<String> getTestModeFromSnapshot(SystemSnapshot systemSnapshot) {
        return systemSnapshot.getTestEgressFlows();
    }
}
