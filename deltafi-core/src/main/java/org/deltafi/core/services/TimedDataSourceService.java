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
package org.deltafi.core.services;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.IngressStatus;
import org.deltafi.common.types.TimedDataSourcePlan;
import org.deltafi.core.converters.TimedDataSourcePlanConverter;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.repo.TimedDataSourceRepo;
import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.types.snapshot.TimedDataSourceSnapshot;
import org.deltafi.core.types.*;
import org.deltafi.core.validation.FlowValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.Objects;

@Service
@Slf4j
public class TimedDataSourceService extends DataSourceService<TimedDataSourcePlan, TimedDataSource, TimedDataSourceSnapshot, TimedDataSourceRepo> {

    private static final TimedDataSourcePlanConverter TIMED_DATA_SOURCE_FLOW_PLAN_CONVERTER = new TimedDataSourcePlanConverter();

    private final Clock clock;
    private final TimedDataSourceRepo timedDataSourceRepo;

    public TimedDataSourceService(TimedDataSourceRepo timedDataSourceRepo, PluginVariableService pluginVariableService,
                                  FlowValidator flowValidator, BuildProperties buildProperties,
                                  ErrorCountService errorCountService, Clock clock, FlowCacheService flowCacheService,
                                  EventService eventService) {
        super(FlowType.TIMED_DATA_SOURCE, timedDataSourceRepo, pluginVariableService, TIMED_DATA_SOURCE_FLOW_PLAN_CONVERTER,
                flowValidator, buildProperties, flowCacheService, eventService, TimedDataSource.class, TimedDataSourcePlan.class,
                errorCountService);

        this.timedDataSourceRepo = timedDataSourceRepo;
        this.clock = clock;
    }

    @Override
    public void updateSnapshot(Snapshot snapshot) {
        snapshot.setTimedDataSources(getAll().stream().map(TimedDataSourceSnapshot::new).toList());
    }

    @Override
    public List<TimedDataSourceSnapshot> getFlowSnapshots(Snapshot snapshot) {
        return snapshot.getTimedDataSources();
    }

    @Override
    protected boolean updateSpecificDataSourceFields(TimedDataSource flow, TimedDataSourceSnapshot dataSourceSnapshot, Result result) {
        return updateTimedDataSourceSpecificFields(flow, dataSourceSnapshot);
    }

    private boolean updateTimedDataSourceSpecificFields(TimedDataSource dataSource, TimedDataSourceSnapshot timedDataSourceSnapshot) {
        if (Objects.equals(dataSource.getCronSchedule(), timedDataSourceSnapshot.getCronSchedule())) {
            return false;
        }

        dataSource.setCronSchedule(timedDataSourceSnapshot.getCronSchedule());
        return true;
    }

    /**
     * Sets the cron schedule for a given dataSource, identified by its name.
     * If the cron schedule for the dataSource is already set to the specified value, the method
     * logs a warning and returns false. If the update is successful, the method refreshes the
     * cache and returns true.
     *
     * @param flowName  The name of the dataSource to update, represented as a {@code String}.
     * @param cronSchedule The new cron schedule to be set for the specified dataSource, as a {@code String}.
     * @return A {@code boolean} value indicating whether the update was successful (true) or not (false).
     */
    public boolean setCronSchedule(String flowName, String cronSchedule) {
        TimedDataSource flow = getFlowOrThrow(flowName);

        if (flow.getCronSchedule().equals(cronSchedule)) {
            log.warn("Tried to set cron schedule on timed data source {} to \"{}\" when already set", flowName, cronSchedule);
            return false;
        }

        CronExpression cronExpression = CronExpression.parse(cronSchedule);
        TimedDataSource updated = timedDataSourceRepo.updateCronSchedule(flowName, cronSchedule, cronExpression.next(OffsetDateTime.now(clock)));
        if (updated != null) {
            flowCacheService.updateCache(updated);
            return true;
        }

        return false;
    }

    /**
     * Sets the memo for a given dataSource, identified by its name.
     * If the memo for the dataSource is already set to the specified value, the method
     * logs a warning and returns false. If the update is successful, the method refreshes the
     * cache and returns true.
     *
     * @param flowName  The name of the dataSource to update, represented as a {@code String}.
     * @param memo The new memo value to be set for the specified dataSource, as a {@code String}.
     * @return A {@code boolean} value indicating whether the update was successful (true) or not (false).
     */
    public boolean setMemo(String flowName, String memo) {
        TimedDataSource flow = getFlowOrThrow(flowName);

        if ((flow.getMemo() == null && memo == null) ||
                (flow.getMemo() != null && flow.getMemo().equals(memo))) {
            log.warn("Tried to set memo on timed data source {} to \"{}\" when already set", flowName, memo);
            return false;
        }

        if (FlowState.RUNNING.equals(flow.getFlowStatus().getState())) {
            log.error("Cannot change memo of timed data source which is running");
            return false;
        }

        TimedDataSource updated = timedDataSourceRepo.updateMemo(flowName, memo);
        if (updated != null) {
            flowCacheService.updateCache(updated);
            return true;
        }

        return false;
    }

    public void setLastRun(String flowName, OffsetDateTime lastRun, UUID currentDid) {
        TimedDataSource updated = timedDataSourceRepo.updateLastRun(flowName, lastRun, currentDid);
        if (updated != null) {
            flowCacheService.updateCache(updated);
        }
    }

    public boolean completeExecution(String flowName, UUID currentDid, String memo, boolean executeImmediate,
                                     IngressStatus status, String statusMessage, String cronSchedule) {
        CronExpression cronExpression = CronExpression.parse(cronSchedule);

        TimedDataSource updated = timedDataSourceRepo.completeExecution(flowName, currentDid, memo, executeImmediate,
                status == null ? IngressStatus.HEALTHY.toString() : status.toString(), statusMessage, cronExpression.next(OffsetDateTime.now(clock)));

        if (updated != null) {
            flowCacheService.updateCache(updated);
            return true;
        }

        return false;
    }
}
