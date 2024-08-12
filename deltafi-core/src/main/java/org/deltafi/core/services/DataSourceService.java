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
import org.deltafi.common.types.DataSourcePlan;
import org.deltafi.common.types.IngressStatus;
import org.deltafi.core.converters.DataSourcePlanConverter;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.DataSourceErrorState;
import org.deltafi.core.repo.DataSourceRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.DataSourceSnapshot;
import org.deltafi.core.snapshot.types.RestDataSourceSnapshot;
import org.deltafi.core.snapshot.types.TimedDataSourceSnapshot;
import org.deltafi.core.types.*;
import org.deltafi.core.validation.DataSourceValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DataSourceService extends FlowService<DataSourcePlan, DataSource, DataSourceSnapshot> {

    private static final DataSourcePlanConverter TIMED_DATA_SOURCE_FLOW_PLAN_CONVERTER = new DataSourcePlanConverter();

    private final ErrorCountService errorCountService;
    private final Clock clock;

    public DataSourceService(DataSourceRepo dataSourceRepo, PluginVariableService pluginVariableService,
                             DataSourceValidator dataSourceValidator, BuildProperties buildProperties,
                             ErrorCountService errorCountService, Clock clock) {
        super("dataSource", dataSourceRepo, pluginVariableService, TIMED_DATA_SOURCE_FLOW_PLAN_CONVERTER,
                dataSourceValidator, buildProperties);
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

    public TimedDataSource getRunningTimedDataSource(String name) {
        return getRunningFromCache(name, TimedDataSource.class);
    }

    public TimedDataSource getTimedDataSource(String name) {
        return getFromDataBase(name, TimedDataSource.class);
    }

    public RestDataSource getRunningRestDataSource(String name) {
        return getRunningFromCache(name, RestDataSource.class);
    }

    public RestDataSource getRestDataSource(String name) {
        return getFromDataBase(name, RestDataSource.class);
    }

    private <T> T getRunningFromCache(String name, Class<T> type) {
        return castObjectToType(getRunningFlowByName(name), type);
    }

    private <T> T getFromDataBase(String name, Class<T> type) {
        return castObjectToType(getFlowOrThrow(name), type);
    }

    private <T> T castObjectToType(DataSource dataSource, Class<T> type) {
        if (dataSource.getClass().isAssignableFrom(type)) {
            return type.cast(dataSource);
        }

        throw new IllegalArgumentException("Data source %s is %s instead of %s".formatted(dataSource.getName(), type.getName(), dataSource.getClass().getName()));
    }

    @Override
    void copyFlowSpecificFields(DataSource sourceFlow, DataSource targetFlow) {
        targetFlow.copyFields(sourceFlow);
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        refreshCache();
        List<RestDataSourceSnapshot> restDataSourceSnapshots = new ArrayList<>();
        List<TimedDataSourceSnapshot> timedDataSourceSnapshots = new ArrayList<>();
        for (DataSource dataSource : getAll()) {
            if (dataSource instanceof RestDataSource restDataSource) {
                restDataSourceSnapshots.add(new RestDataSourceSnapshot(restDataSource));
            } else if (dataSource instanceof TimedDataSource timedDataSource) {
                timedDataSourceSnapshots.add(new TimedDataSourceSnapshot(timedDataSource));
            }
        }
        systemSnapshot.setRestDataSources(restDataSourceSnapshots);
        systemSnapshot.setTimedDataSources(timedDataSourceSnapshots);
    }

    @Override
    public List<DataSourceSnapshot> getFlowSnapshots(SystemSnapshot systemSnapshot) {
        List<DataSourceSnapshot> dataSourceSnapshots = new ArrayList<>();
        dataSourceSnapshots.addAll(systemSnapshot.getRestDataSources());
        dataSourceSnapshots.addAll(systemSnapshot.getTimedDataSources());
        return dataSourceSnapshots;
    }

    @Override
    public boolean flowSpecificUpdateFromSnapshot(DataSource flow, DataSourceSnapshot dataSourceSnapshot, Result result) {
        boolean changed = false;
        if (!Objects.equals(flow.getTopic(), dataSourceSnapshot.getTopic())) {
            flow.setTopic(dataSourceSnapshot.getTopic());
            changed = true;
        }

        if (flow.getMaxErrors() != dataSourceSnapshot.getMaxErrors()) {
            flow.setMaxErrors(dataSourceSnapshot.getMaxErrors());
            changed = true;
        }

        if (flow instanceof TimedDataSource timedDataSource && dataSourceSnapshot instanceof TimedDataSourceSnapshot timedDataSourceSnapshot) {
            return flowSpecificUpdateFromSnapshot(timedDataSource, timedDataSourceSnapshot) || changed;
        } else if (flow instanceof RestDataSource && dataSourceSnapshot instanceof RestDataSourceSnapshot) {
            return changed;
        }

        log.warn("Invalid flow and snapshot types ({} with {})", flow.getClass().getName(), dataSourceSnapshot.getClass().getName());
        return false;
    }

    private boolean flowSpecificUpdateFromSnapshot(TimedDataSource dataSource, TimedDataSourceSnapshot timedDataSourceSnapshot) {
        if (Objects.equals(dataSource.getCronSchedule(), timedDataSourceSnapshot.getCronSchedule())) {
            return false;
        }

        dataSource.setCronSchedule(timedDataSourceSnapshot.getCronSchedule());
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
        TimedDataSource flow = getTimedDataSource(flowName);

        if (flow.getCronSchedule().equals(cronSchedule)) {
            log.warn("Tried to set cron schedule on timed data source {} to \"{}\" when already set", flowName, cronSchedule);
            return false;
        }

        CronExpression cronExpression = CronExpression.parse(cronSchedule);
        if (((DataSourceRepo) flowRepo).updateCronSchedule(flowName, cronSchedule,
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
        TimedDataSource flow = getTimedDataSource(flowName);

        if ((flow.getMemo() == null && memo == null) ||
                (flow.getMemo() != null && flow.getMemo().equals(memo))) {
            log.warn("Tried to set memo on timed data source {} to \"{}\" when already set", flowName, memo);
            return false;
        }

        if (FlowState.RUNNING.equals(flow.getFlowStatus().getState())) {
            log.error("Cannot change memo of timed data source which is running");
            return false;
        }

        if (((DataSourceRepo) flowRepo).updateMemo(flowName, memo)) {
            refreshCache();
            return true;
        }

        return false;
    }

    public boolean setLastRun(String flowName, OffsetDateTime lastRun, UUID currentDid) {
        if (((DataSourceRepo) flowRepo).updateLastRun(flowName, lastRun, currentDid)) {
            refreshCache();
            return true;
        }

        return false;
    }

    public boolean completeExecution(String flowName, UUID currentDid, String memo, boolean executeImmediate,
                                     IngressStatus status, String statusMessage, String cronSchedule) {
        CronExpression cronExpression = CronExpression.parse(cronSchedule);
        if (((DataSourceRepo) flowRepo).completeExecution(flowName, currentDid, memo, executeImmediate,
                status == null ? IngressStatus.HEALTHY : status, statusMessage, cronExpression.next(OffsetDateTime.now(clock)))) {
            refreshCache();
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
        DataSource flow = getFlowOrThrow(flowName);

        if (flow.getMaxErrors() == maxErrors) {
            log.warn("Tried to set max errors on transform flow {} to {} when already set", flowName, maxErrors);
            return false;
        }

        if (((DataSourceRepo) flowRepo).updateMaxErrors(flowName, maxErrors)) {
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
