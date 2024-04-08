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
import org.deltafi.common.types.IngressStatus;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.TimedIngressFlowPlan;
import org.deltafi.core.converters.TimedIngressFlowPlanConverter;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.repo.TimedIngressFlowRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.TimedIngressFlowSnapshot;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.TimedIngressFlow;
import org.deltafi.core.validation.TimedIngressFlowValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TimedIngressFlowService extends FlowService<TimedIngressFlowPlan, TimedIngressFlow, TimedIngressFlowSnapshot> {

    private static final TimedIngressFlowPlanConverter TIMED_INGRESS_FLOW_PLAN_CONVERTER = new TimedIngressFlowPlanConverter();

    private final Clock clock;

    public TimedIngressFlowService(TimedIngressFlowRepo timedIngressFlowRepo, PluginVariableService pluginVariableService,
            TimedIngressFlowValidator timedIngressFlowValidator, BuildProperties buildProperties, Clock clock) {
        super("timedIngress", timedIngressFlowRepo, pluginVariableService, TIMED_INGRESS_FLOW_PLAN_CONVERTER,
                timedIngressFlowValidator, buildProperties);

        this.clock = clock;
    }

    @Override
    void copyFlowSpecificFields(TimedIngressFlow sourceFlow, TimedIngressFlow targetFlow) {
        targetFlow.setLastRun(sourceFlow.getLastRun());
        targetFlow.setNextRun(sourceFlow.getNextRun());
        targetFlow.setMemo(sourceFlow.getMemo());
        targetFlow.setCurrentDid(sourceFlow.getCurrentDid());
        targetFlow.setExecuteImmediate(sourceFlow.isExecuteImmediate());
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
    public boolean flowSpecificUpdateFromSnapshot(TimedIngressFlow flow, TimedIngressFlowSnapshot snapshot, Result result) {
        if (flow.getTargetFlow().equals(snapshot.getTargetFlow()) && flow.getCronSchedule().equals(snapshot.getCronSchedule())) {
            return false;
        }

        flow.setTargetFlow(snapshot.getTargetFlow());
        flow.setCronSchedule(snapshot.getCronSchedule());

        return true;
    }

    /**
     * Sets the cron schedule for a given flow, identified by its name.
     * If the cron schedule for the flow is already set to the specified value, the method
     * logs a warning and returns false. If the update is successful, the method refreshes the
     * cache and returns true.
     *
     * @param flowName  The name of the flow to update, represented as a {@code String}.
     * @param cronSchedule The new cron schedule to be set for the specified flow, as a {@code String}.
     * @return A {@code boolean} value indicating whether the update was successful (true) or not (false).
     */
    public boolean setCronSchedule(String flowName, String cronSchedule) {
        TimedIngressFlow flow = getFlowOrThrow(flowName);

        if (flow.getCronSchedule().equals(cronSchedule)) {
            log.warn("Tried to set cron schedule on timed ingress flow {} to \"{}\" when already set", flowName, cronSchedule);
            return false;
        }

        CronExpression cronExpression = CronExpression.parse(cronSchedule);
        if (((TimedIngressFlowRepo) flowRepo).updateCronSchedule(flowName, cronSchedule,
                cronExpression.next(OffsetDateTime.now(clock)))) {
            refreshCache();
            return true;
        }

        return false;
    }

    /**
     * Sets the memo for a given flow, identified by its name.
     * If the memo for the flow is already set to the specified value, the method
     * logs a warning and returns false. If the update is successful, the method refreshes the
     * cache and returns true.
     *
     * @param flowName  The name of the flow to update, represented as a {@code String}.
     * @param memo The new memo value to be set for the specified flow, as a {@code String}.
     * @return A {@code boolean} value indicating whether the update was successful (true) or not (false).
     */
    public boolean setMemo(String flowName, String memo) {
        TimedIngressFlow flow = getFlowOrThrow(flowName);

        if ((flow.getMemo() == null && memo == null) ||
                (flow.getMemo() != null && flow.getMemo().equals(memo))) {
            log.warn("Tried to set memo on timed ingress flow {} to \"{}\" when already set", flowName, memo);
            return false;
        }

        if (FlowState.RUNNING.equals(flow.getFlowStatus().getState())) {
            log.error("Cannot change memo of timed ingress flow which is running");
            return false;
        }

        if (((TimedIngressFlowRepo) flowRepo).updateMemo(flowName, memo)) {
            refreshCache();
            return true;
        }

        return false;
    }

    public boolean setLastRun(String flowName, OffsetDateTime lastRun, String currentDid) {
        if (((TimedIngressFlowRepo) flowRepo).updateLastRun(flowName, lastRun, currentDid)) {
            refreshCache();
            return true;
        }

        return false;
    }

    public boolean completeExecution(String flowName, String currentDid, String memo, boolean executeImmediate,
            IngressStatus status, String statusMessage, String cronSchedule) {
        CronExpression cronExpression = CronExpression.parse(cronSchedule);
        if (((TimedIngressFlowRepo) flowRepo).completeExecution(flowName, currentDid, memo, executeImmediate,
                status == null ? IngressStatus.HEALTHY : status, statusMessage, cronExpression.next(OffsetDateTime.now(clock)))) {
            refreshCache();
            return true;
        }

        return false;
    }
}
