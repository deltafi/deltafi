/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.generated.types.DataSourceErrorState;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.repo.TimedDataSourceRepo;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.snapshot.TimedDataSourceSnapshot;
import org.deltafi.core.types.*;
import org.deltafi.core.validation.FlowValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TimedDataSourceService extends FlowService<TimedDataSourcePlan, TimedDataSource, TimedDataSourceSnapshot, TimedDataSourceRepo> {

    private static final TimedDataSourcePlanConverter TIMED_DATA_SOURCE_FLOW_PLAN_CONVERTER = new TimedDataSourcePlanConverter();

    private final ErrorCountService errorCountService;
    private final Clock clock;
    private final TimedDataSourceRepo timedDataSourceRepo;

    public TimedDataSourceService(TimedDataSourceRepo timedDataSourceRepo, PluginVariableService pluginVariableService,
                                  FlowValidator flowValidator, BuildProperties buildProperties,
                                  ErrorCountService errorCountService, Clock clock, FlowCacheService flowCacheService) {
        super(FlowType.TIMED_DATA_SOURCE, timedDataSourceRepo, pluginVariableService, TIMED_DATA_SOURCE_FLOW_PLAN_CONVERTER,
                flowValidator, buildProperties, flowCacheService, TimedDataSource.class, TimedDataSourcePlan.class);

        this.timedDataSourceRepo = timedDataSourceRepo;
        this.errorCountService = errorCountService;
        this.clock = clock;
    }

    public List<TimedDataSource> getRunningTimedDataSources() {
        return getAll().stream()
                .map(this::toTimedDataSource)
                .filter(Objects::nonNull)
                .toList();
    }

    // if this is running and a timed data source return it, otherwise return null
    private TimedDataSource toTimedDataSource(DataSource dataSource) {
        if (dataSource.isRunning() && dataSource instanceof TimedDataSource timedDataSource) {
            return timedDataSource;
        }

        return null;
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        List<TimedDataSourceSnapshot> timedDataSourceSnapshots = new ArrayList<>();
        for (TimedDataSource timedDataSource : getAll()) {
            timedDataSourceSnapshots.add(new TimedDataSourceSnapshot(timedDataSource));
        }
        systemSnapshot.setTimedDataSources(timedDataSourceSnapshots);
    }

    @Override
    public List<TimedDataSourceSnapshot> getFlowSnapshots(SystemSnapshot systemSnapshot) {
        return systemSnapshot.getTimedDataSources();
    }

    @Override
    public boolean flowSpecificUpdateFromSnapshot(TimedDataSource flow, TimedDataSourceSnapshot dataSourceSnapshot, Result result) {
        boolean changed = false;
        if (!Objects.equals(flow.getTopic(), dataSourceSnapshot.getTopic())) {
            flow.setTopic(dataSourceSnapshot.getTopic());
            changed = true;
        }

        if (flow.getMaxErrors() != dataSourceSnapshot.getMaxErrors()) {
            flow.setMaxErrors(dataSourceSnapshot.getMaxErrors());
            changed = true;
        }

        return flowSpecificUpdateFromSnapshot(flow, dataSourceSnapshot) || changed;
    }

    private boolean flowSpecificUpdateFromSnapshot(TimedDataSource dataSource, TimedDataSourceSnapshot timedDataSourceSnapshot) {
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
        if (timedDataSourceRepo.updateCronSchedule(flowName, cronSchedule, cronExpression.next(OffsetDateTime.now(clock))) > 0) {
            flowCacheService.refreshCache();
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

        if (timedDataSourceRepo.updateMemo(flowName, memo) > 0) {
            flowCacheService.refreshCache();
            return true;
        }

        return false;
    }

    public void setLastRun(String flowName, OffsetDateTime lastRun, UUID currentDid) {
        if (timedDataSourceRepo.updateLastRun(flowName, lastRun, currentDid) > 0) {
            flowCacheService.refreshCache();
        }

    }

    public boolean completeExecution(String flowName, UUID currentDid, String memo, boolean executeImmediate,
                                     IngressStatus status, String statusMessage, String cronSchedule) {
        CronExpression cronExpression = CronExpression.parse(cronSchedule);

        if (timedDataSourceRepo.completeExecution(flowName, currentDid, memo, executeImmediate,
                status == null ? IngressStatus.HEALTHY : status, statusMessage, cronExpression.next(OffsetDateTime.now(clock))) > 0) {
            flowCacheService.refreshCache();
            return true;
        }

        return false;
    }

    /**
     * Sets the maximum number of errors allowed for a given dataSource, identified by its name.
     * If the maximum errors for the dataSource are already set to the specified value, the method
     * logs a warning and returns false. If the update is successful, the method refreshes the
     * cache and returns true.
     *
     * @param flowName  The name of the dataSource to update, represented as a {@code String}.
     * @param maxErrors The new maximum number of errors to be set for the specified dataSource, as an {@code int}.
     * @return A {@code boolean} value indicating whether the update was successful (true) or not (false).
     */
    public boolean setMaxErrors(String flowName, int maxErrors) {
        TimedDataSource flow = getFlowOrThrow(flowName);

        if (flow.getMaxErrors() == maxErrors) {
            log.warn("Tried to set max errors on transform dataSource {} to {} when already set", flowName, maxErrors);
            return false;
        }

        if (flowRepo.updateMaxErrors(flowName, maxErrors) > 0) {
            flowCacheService.refreshCache();
            return true;
        }

        return false;
    }

    /**
     * Retrieves a map containing the maximum number of errors allowed per dataSource.
     * This method filters out flows with a maximum error count of 0, only including
     * those with a positive maximum error count.
     *
     * @return A {@code Map<String, Integer>} where each key represents a dataSource name,
     * and the corresponding value is the maximum number of errors allowed for that dataSource.
     */
    public Map<String, Integer> maxErrorsPerFlow() {
        return getRunningFlows().stream()
                .filter(e -> e.getMaxErrors() >= 0)
                .collect(Collectors.toMap(Flow::getName, DataSource::getMaxErrors));
    }

    /**
     * Get a list of DataSourceErrorStates for data sources that have
     * exceeded their max allowed errors
     * @return list of IngressFlowErrorStates
     */
    public List<DataSourceErrorState> dataSourceErrorsExceeded() {
        return getRunningFlows().stream()
                .map(f -> new DataSourceErrorState(f.getName(), errorCountService.errorsForFlow(f.getName()), f.getMaxErrors()))
                .filter(s -> s.getMaxErrors() >= 0 && s.getCurrErrors() > s.getMaxErrors())
                .toList();
    }
}
