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

import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.generated.types.DataSourceErrorState;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.repo.TimedDataSourceRepo;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.snapshot.FlowSnapshot;
import org.deltafi.core.types.snapshot.TimedDataSourceSnapshot;
import org.deltafi.core.types.*;
import org.deltafi.core.validation.TimedDataSourceValidator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.core.datafetchers.FlowPlanDatafetcherTestHelper.PLUGIN_COORDINATES;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class TimedDataSourceServiceTest {

    @Mock
    TimedDataSourceRepo timedDataSourceRepo;

    @Mock
    TimedDataSourceValidator flowValidator;

    @Mock
    @SuppressWarnings("unused")
    BuildProperties buildProperties;

    @InjectMocks
    TimedDataSourceService timedDataSourceService;

    @Mock
    ErrorCountService errorCountService;

    @Captor
    ArgumentCaptor<List<TimedDataSource>> flowCaptor;

    @Test
    void buildFlow() {
        TimedDataSource running = timedDataSource("running", FlowState.RUNNING, true,"0 */10 * * * *", 10);
        TimedDataSource stopped = timedDataSource("stopped", FlowState.STOPPED, false, "*/1 * * * * *", -1);
        Mockito.when(timedDataSourceRepo.findByNameAndType("running", TimedDataSource.class)).thenReturn(Optional.of(running));
        Mockito.when(timedDataSourceRepo.findByNameAndType("stopped", TimedDataSource.class)).thenReturn(Optional.of(stopped));
        Mockito.when(flowValidator.validate(Mockito.any())).thenReturn(Collections.emptyList());

        TimedDataSourcePlanEntity runningFlowPlan = new TimedDataSourcePlanEntity("running", "yep", PLUGIN_COORDINATES, "topic",
                new ActionConfiguration("TimedIngressActionConfig", ActionType.TIMED_INGRESS, "TimedIngressActionConfigType"),
                "0 */10 * * * *");
        TimedDataSourcePlanEntity stoppedFlowPlan = new TimedDataSourcePlanEntity("stopped", "naw", PLUGIN_COORDINATES, "topic",
                new ActionConfiguration("TimedIngressActionConfig", ActionType.TIMED_INGRESS, "TimedIngressActionConfigType"),
                "*/1 * * * * *");

        TimedDataSource runningDataSource = timedDataSourceService.buildFlow(runningFlowPlan, Collections.emptyList());
        TimedDataSource stoppedDataSource = timedDataSourceService.buildFlow(stoppedFlowPlan, Collections.emptyList());

        assertThat(runningDataSource).isInstanceOf(TimedDataSource.class);
        assertThat(runningDataSource.isRunning()).isTrue();
        assertThat(runningDataSource.isTestMode()).isTrue();
        assertThat(runningDataSource.getCronSchedule()).isEqualTo("0 */10 * * * *");
        assertThat(runningDataSource.getMaxErrors()).isEqualTo(10);
        assertThat(stoppedDataSource.isRunning()).isFalse();
        assertThat(stoppedDataSource.isTestMode()).isFalse();
    }

    @Test
    void updateSnapshot() {
        List<TimedDataSource> flows = new ArrayList<>();
        flows.add(timedDataSource("a", FlowState.RUNNING, false, "0 */1 * * * *", -1));
        flows.add(timedDataSource("b", FlowState.STOPPED, false, "0 */2 * * * *", 1));
        flows.add(timedDataSource("c", FlowState.INVALID, true, "0 */3 * * * *", 1));
        Mockito.when(timedDataSourceRepo.findAllByType(TimedDataSource.class)).thenReturn(flows);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        timedDataSourceService.updateSnapshot(systemSnapshot);

        assertThat(systemSnapshot.getTimedDataSources()).hasSize(3);

        Map<String, TimedDataSourceSnapshot> timedIngressFlowSnapshotMap = systemSnapshot.getTimedDataSources().stream()
                .collect(Collectors.toMap(FlowSnapshot::getName, Function.identity()));

        TimedDataSourceSnapshot aFlowSnapshot = timedIngressFlowSnapshotMap.get("a");
        assertThat(aFlowSnapshot.isRunning()).isTrue();
        assertThat(aFlowSnapshot.isTestMode()).isFalse();
        assertThat(aFlowSnapshot.getCronSchedule()).isEqualTo("0 */1 * * * *");
        assertThat(aFlowSnapshot.getMaxErrors()).isEqualTo(-1);

        TimedDataSourceSnapshot bFlowSnapshot = timedIngressFlowSnapshotMap.get("b");
        assertThat(bFlowSnapshot.isRunning()).isFalse();
        assertThat(bFlowSnapshot.isTestMode()).isFalse();
        assertThat(bFlowSnapshot.getCronSchedule()).isEqualTo("0 */2 * * * *");
        assertThat(bFlowSnapshot.getMaxErrors()).isEqualTo(1);

        TimedDataSourceSnapshot cFlowSnapshot = timedIngressFlowSnapshotMap.get("c");
        assertThat(cFlowSnapshot.isRunning()).isFalse();
        assertThat(cFlowSnapshot.isTestMode()).isTrue();
        assertThat(cFlowSnapshot.getCronSchedule()).isEqualTo("0 */3 * * * *");
        assertThat(cFlowSnapshot.getMaxErrors()).isEqualTo(1);
    }

    @Test
    void testResetFromSnapshot() {
        TimedDataSource running = timedDataSource("running", FlowState.RUNNING, true, "0 */1 * * * *", -1);
        TimedDataSource stopped = timedDataSource("stopped", FlowState.STOPPED, false, "0 */2 * * * *", 1);
        TimedDataSource invalid = timedDataSource("invalid", FlowState.INVALID, false, "0 */3 * * * *", 1);
        TimedDataSource changed = timedDataSource("changed", FlowState.STOPPED, false, "0 0 0 */7 * *", 2);

        SystemSnapshot systemSnapshot = new SystemSnapshot();

        // create snapshot objects
        List<TimedDataSourceSnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot("running", true, false, "0 */1 * * * *", -1));
        snapshots.add(snapshot("stopped", true, true, "0 */2 * * * *", 2));
        snapshots.add(snapshot("invalid", true, false, "0 */3 * * * *", 1));
        snapshots.add(snapshot("changed", false, true, "0 */4 * * * *", -1));
        snapshots.add(snapshot("missing", false, true, "*/1 * * * * *", 3));
        systemSnapshot.setTimedDataSources(snapshots);

        Mockito.when(timedDataSourceRepo.findAllByType(TimedDataSource.class)).thenReturn(List.of(running, stopped, invalid, changed));
        Mockito.when(timedDataSourceRepo.findByNameAndType("running", TimedDataSource.class)).thenReturn(Optional.of(running));
        Mockito.when(timedDataSourceRepo.findByNameAndType("stopped", TimedDataSource.class)).thenReturn(Optional.of(stopped));
        Mockito.when(timedDataSourceRepo.findByNameAndType("invalid", TimedDataSource.class)).thenReturn(Optional.of(invalid));
        Mockito.when(timedDataSourceRepo.findByNameAndType("missing", TimedDataSource.class)).thenReturn(Optional.empty());
        Mockito.when(timedDataSourceRepo.findByNameAndType("changed", TimedDataSource.class)).thenReturn(Optional.of(changed));

        Result result = timedDataSourceService.resetFromSnapshot(systemSnapshot, true);

        // verify the hard reset stopped any running flows
        Mockito.verify(timedDataSourceRepo).updateFlowStatusState("running", FlowState.STOPPED, FlowType.TIMED_DATA_SOURCE);
        Mockito.verify(timedDataSourceRepo).saveAll(flowCaptor.capture());

        Map<String, DataSource> updatedFlows = flowCaptor.getValue().stream()
                .collect(Collectors.toMap(Flow::getName, Function.identity()));


        assertThat(updatedFlows).hasSize(2);

        TimedDataSource updatedStop = getAsTimedDataSource(updatedFlows, "stopped");
        TimedDataSource updatedChanged = getAsTimedDataSource(updatedFlows, "changed");

        // stopped flow should be restarted since it was marked as running in the snapshot, it should also be in test mode
        assertThat(updatedStop).isNotNull();
        assertThat(updatedStop.isRunning()).isTrue();
        assertThat(updatedStop.isTestMode()).isTrue();
        assertThat(updatedStop.getCronSchedule()).isEqualTo("0 */2 * * * *");
        assertThat(updatedStop.getMaxErrors()).isEqualTo(2);

        assertThat(updatedChanged).isNotNull();
        assertThat(updatedChanged.getCronSchedule()).isEqualTo("0 */4 * * * *");
        assertThat(updatedChanged.getMaxErrors()).isEqualTo(-1);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getInfo()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .contains("Flow missing is no longer installed")
                .contains("Flow: invalid is invalid and cannot be started");
    }

    @Test
    void testDataSourceErrorsExceeded() {
        setupErrorExceeded();
        List<DataSourceErrorState> errorStates = timedDataSourceService.dataSourceErrorsExceeded();
        assertEquals(2, errorStates.size());
        assertEquals(new DataSourceErrorState("flow1", 1, 0), errorStates.get(0));
        assertEquals(new DataSourceErrorState("flow3", 6, 5), errorStates.get(1));
    }

    void setupErrorExceeded() {
        TimedDataSource flow1 = timedDataSource("flow1", FlowState.RUNNING, false, "0 */4 * * * *", 0);
        TimedDataSource flow2 = timedDataSource("flow2", FlowState.RUNNING, false, "0 */4 * * * *", 5);
        TimedDataSource flow3 = timedDataSource("flow3", FlowState.RUNNING, false, "0 */4 * * * *", 5);
        TimedDataSource flow4 = timedDataSource("flow4", FlowState.STOPPED, false, "0 */4 * * * *", 5);

        Mockito.when(timedDataSourceRepo.findAllByType(TimedDataSource.class)).thenReturn(List.of(flow1, flow2, flow3, flow4));
        Mockito.when(errorCountService.errorsForFlow("flow1")).thenReturn(1);
        Mockito.when(errorCountService.errorsForFlow("flow2")).thenReturn(5);
        Mockito.when(errorCountService.errorsForFlow("flow3")).thenReturn(6);

        timedDataSourceService.refreshCache();
    }

    private TimedDataSource getAsTimedDataSource(Map<String, DataSource> dataSourceMap, String name) {
        DataSource dataSource = dataSourceMap.get(name);
        if (dataSource instanceof TimedDataSource timedDataSource) {
            return timedDataSource;
        }

        Assertions.fail("Invalid data source type " + dataSource.getType());
        return null;
    }

    TimedDataSource timedDataSource(String name, FlowState flowState, boolean testMode, String cronSchedule, int maxErrors) {
        TimedDataSource dataSource = new TimedDataSource();
        dataSource.setName(name);
        dataSource.setMaxErrors(maxErrors);
        FlowStatus flowStatus = new FlowStatus();
        flowStatus.setState(flowState);
        flowStatus.setTestMode(testMode);
        dataSource.setFlowStatus(flowStatus);
        dataSource.setCronSchedule(cronSchedule);
        return dataSource;
    }

    TimedDataSourceSnapshot snapshot(String name, boolean running, boolean testMode, String cronSchedule, int maxErrors) {
        TimedDataSourceSnapshot snapshot = new TimedDataSourceSnapshot(name);
        snapshot.setRunning(running);
        snapshot.setTestMode(testMode);
        snapshot.setCronSchedule(cronSchedule);
        snapshot.setMaxErrors(maxErrors);
        return snapshot;
    }
}