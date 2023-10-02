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
import org.deltafi.common.types.TimedIngressFlowPlan;
import org.deltafi.core.converters.TimedIngressFlowPlanConverter;
import org.deltafi.core.repo.TimedIngressFlowRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.TimedIngressFlowSnapshot;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.TimedIngressFlow;
import org.deltafi.core.validation.TimedIngressFlowValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class TimedIngressFlowService extends FlowService<TimedIngressFlowPlan, TimedIngressFlow, TimedIngressFlowSnapshot> {

    private static final TimedIngressFlowPlanConverter TIMED_INGRESS_FLOW_PLAN_CONVERTER = new TimedIngressFlowPlanConverter();

    public TimedIngressFlowService(TimedIngressFlowRepo timedIngressFlowRepo, PluginVariableService pluginVariableService, TimedIngressFlowValidator timedIngressFlowValidator, BuildProperties buildProperties) {
        super("timedIngress", timedIngressFlowRepo, pluginVariableService, TIMED_INGRESS_FLOW_PLAN_CONVERTER, timedIngressFlowValidator, buildProperties);
    }

    @Override
    void copyFlowSpecificFields(TimedIngressFlow sourceFlow, TimedIngressFlow targetFlow) {
        targetFlow.setInterval(sourceFlow.getInterval());
        targetFlow.setTargetFlow(sourceFlow.getTargetFlow());
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        refreshCache();
        systemSnapshot.setTimedIngressFlows(getAll().stream().map(TimedIngressFlowSnapshot::new).toList());
    }

    @Override
    public List<TimedIngressFlowSnapshot> getFlowSnapshots(SystemSnapshot systemSnapshot) {
        return systemSnapshot.getTimedIngressFlows();
    }

    @Override
    public boolean flowSpecificUpdateFromSnapshot(TimedIngressFlow flow, TimedIngressFlowSnapshot timedIngressFlowSnapshot, Result result) {
        if (flow.getInterval() != timedIngressFlowSnapshot.getInterval() ||
                !Objects.equals(flow.getTargetFlow(), timedIngressFlowSnapshot.getTargetFlow())) {
            flow.setInterval(timedIngressFlowSnapshot.getInterval());
            flow.setTargetFlow(timedIngressFlowSnapshot.getTargetFlow());
            return true;
        }

        return false;
    }

    /**
     * Sets the interval for a given flow, identified by its name.
     * If the interval for the flow is already set to the specified value, the method
     * logs a warning and returns false. If the update is successful, the method refreshes the
     * cache and returns true.
     *
     * @param flowName  The name of the flow to update, represented as a {@code String}.
     * @param interval The new interval to be set for the specified flow, as a {@code Duration}.
     * @return A {@code boolean} value indicating whether the update was successful (true) or not (false).
     */
    public boolean setInterval(String flowName, Duration interval) {
        TimedIngressFlow flow = getFlowOrThrow(flowName);

        if (flow.getInterval() == interval) {
            log.warn("Tried to set interval on timed ingress flow {} to {} when already set", flowName, interval);
            return false;
        }

        if (((TimedIngressFlowRepo) flowRepo).updateInterval(flowName, interval)) {
            refreshCache();
            return true;
        }

        return false;
    }

    public boolean setLastRun(String flowName, OffsetDateTime lastRun) {
        if (((TimedIngressFlowRepo) flowRepo).updateLastRun(flowName, lastRun)) {
            refreshCache();
            return true;
        }

        return false;
    }
}
