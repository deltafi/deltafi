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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.generated.types.DeltaFileStats;
import org.deltafi.core.monitor.MonitorResult;
import org.deltafi.core.types.leader.AggregatedStats;
import org.deltafi.core.types.leader.ConnectionState;
import org.deltafi.core.types.leader.MemberConfig;
import org.deltafi.core.types.leader.MemberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;

import java.net.http.HttpClient;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberMonitorServiceTest {

    @Mock
    private DeltaFiPropertiesService deltaFiPropertiesService;

    @Mock
    private ValkeyKeyedBlockingQueue valkeyQueue;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpClient httpClient;

    @Mock
    private SystemService systemService;

    @Mock
    private DeltaFilesService deltaFilesService;

    @Mock
    private BuildProperties buildProperties;

    @Mock
    private GraphiteQueryService graphiteQueryService;

    @Mock
    private DeltaFiProperties deltaFiProperties;

    private MemberMonitorService memberMonitorService;

    @BeforeEach
    void setUp() {
        lenient().when(deltaFiProperties.getMemberPollingTimeout()).thenReturn(5000);
        lenient().when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of());
        lenient().when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);

        memberMonitorService = new MemberMonitorService(
                deltaFiPropertiesService,
                valkeyQueue,
                objectMapper,
                httpClient,
                systemService,
                deltaFilesService,
                buildProperties,
                graphiteQueryService
        );
    }

    @Test
    void getAggregatedStats_aggregatesTotalsCorrectly() {
        // Setup leader status
        MonitorResult healthyStatus = createHealthyMonitorResult();
        SystemService.Status leaderStatus = mock(SystemService.Status.class);
        when(systemService.systemStatus()).thenReturn(leaderStatus);
        when(deltaFilesService.deltaFileStats()).thenReturn(createDeltaFileStats(100L, 50L, 25L));
        when(deltaFilesService.countUnacknowledgedErrors()).thenReturn(5L);
        when(buildProperties.getVersion()).thenReturn("1.0.0");

        try {
            when(objectMapper.treeToValue(any(), eq(MonitorResult.class))).thenReturn(healthyStatus);
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        // Setup member statuses in Valkey
        MemberStatus member1 = createMemberStatus("member1", ConnectionState.CONNECTED, healthyStatus, 200L, 10L, 30L, 15L);
        MemberStatus member2 = createMemberStatus("member2", ConnectionState.CONNECTED, healthyStatus, 150L, 3L, 20L, 10L);

        when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                new MemberConfig("member1", "http://member1", List.of(), null),
                new MemberConfig("member2", "http://member2", List.of(), null)
        ));

        try {
            when(valkeyQueue.getByKey("org.deltafi.leader.member.member1"))
                    .thenReturn("{\"memberName\":\"member1\"}");
            when(valkeyQueue.getByKey("org.deltafi.leader.member.member2"))
                    .thenReturn("{\"memberName\":\"member2\"}");
            when(objectMapper.readValue("{\"memberName\":\"member1\"}", MemberStatus.class)).thenReturn(member1);
            when(objectMapper.readValue("{\"memberName\":\"member2\"}", MemberStatus.class)).thenReturn(member2);
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        AggregatedStats stats = memberMonitorService.getAggregatedStats();

        // Leader: inFlight=100, errors=5, warm=50, cold=25
        // Member1: inFlight=200, errors=10, warm=30, cold=15
        // Member2: inFlight=150, errors=3, warm=20, cold=10
        assertEquals(450L, stats.totalInFlight()); // 100 + 200 + 150
        assertEquals(18L, stats.totalErrors()); // 5 + 10 + 3
        assertEquals(100L, stats.totalWarmQueue()); // 50 + 30 + 20
        assertEquals(50L, stats.totalColdQueue()); // 25 + 15 + 10
        assertEquals(3, stats.memberCount()); // leader + 2 members
    }

    @Test
    void getAggregatedStats_countsHealthyAndUnhealthyCorrectly() {
        MonitorResult healthyStatus = createHealthyMonitorResult();
        MonitorResult degradedStatus = createDegradedMonitorResult();
        SystemService.Status leaderStatus = mock(SystemService.Status.class);
        when(systemService.systemStatus()).thenReturn(leaderStatus);
        when(deltaFilesService.deltaFileStats()).thenReturn(createDeltaFileStats(0L, 0L, 0L));
        when(deltaFilesService.countUnacknowledgedErrors()).thenReturn(0L);
        when(buildProperties.getVersion()).thenReturn("1.0.0");

        try {
            when(objectMapper.treeToValue(any(), eq(MonitorResult.class))).thenReturn(healthyStatus);
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        // Setup: healthy leader, healthy member1, degraded member2, unreachable member3
        MemberStatus member1 = createMemberStatus("member1", ConnectionState.CONNECTED, healthyStatus, 0L, 0L, 0L, 0L);
        MemberStatus member2 = createMemberStatus("member2", ConnectionState.CONNECTED, degradedStatus, 0L, 0L, 0L, 0L);
        MemberStatus member3 = createMemberStatus("member3", ConnectionState.UNREACHABLE, null, null, null, null, null);

        when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                new MemberConfig("member1", "http://member1", List.of(), null),
                new MemberConfig("member2", "http://member2", List.of(), null),
                new MemberConfig("member3", "http://member3", List.of(), null)
        ));

        try {
            when(valkeyQueue.getByKey("org.deltafi.leader.member.member1")).thenReturn("{}");
            when(valkeyQueue.getByKey("org.deltafi.leader.member.member2")).thenReturn("{}");
            when(valkeyQueue.getByKey("org.deltafi.leader.member.member3")).thenReturn("{}");
            when(objectMapper.readValue("{}", MemberStatus.class))
                    .thenReturn(member1)
                    .thenReturn(member2)
                    .thenReturn(member3);
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        AggregatedStats stats = memberMonitorService.getAggregatedStats();

        assertEquals(4, stats.memberCount()); // leader + 3 members
        assertEquals(2, stats.healthyCount()); // leader + member1
        assertEquals(2, stats.unhealthyCount()); // member2 (degraded) + member3 (unreachable)
    }

    @Test
    void getAggregatedStats_handlesNullMetricsGracefully() {
        MonitorResult healthyStatus = createHealthyMonitorResult();
        SystemService.Status leaderStatus = mock(SystemService.Status.class);
        when(systemService.systemStatus()).thenReturn(leaderStatus);
        when(deltaFilesService.deltaFileStats()).thenReturn(createDeltaFileStats(100L, 0L, 0L));
        when(deltaFilesService.countUnacknowledgedErrors()).thenReturn(0L);
        when(buildProperties.getVersion()).thenReturn("1.0.0");

        try {
            when(objectMapper.treeToValue(any(), eq(MonitorResult.class))).thenReturn(healthyStatus);
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        // Member with all null metrics
        MemberStatus memberWithNulls = createMemberStatus("member1", ConnectionState.STALE, null, null, null, null, null);

        when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                new MemberConfig("member1", "http://member1", List.of(), null)
        ));

        try {
            when(valkeyQueue.getByKey("org.deltafi.leader.member.member1")).thenReturn("{}");
            when(objectMapper.readValue("{}", MemberStatus.class)).thenReturn(memberWithNulls);
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        AggregatedStats stats = memberMonitorService.getAggregatedStats();

        // Should only include leader's metrics, nulls should be treated as 0
        assertEquals(100L, stats.totalInFlight());
        assertEquals(0L, stats.totalErrors());
        assertEquals(2, stats.memberCount());
        assertEquals(1, stats.healthyCount()); // just leader
        assertEquals(1, stats.unhealthyCount()); // member with null status
    }

    @Test
    void getAggregatedStats_emptyMemberList() {
        MonitorResult healthyStatus = createHealthyMonitorResult();
        SystemService.Status leaderStatus = mock(SystemService.Status.class);
        when(systemService.systemStatus()).thenReturn(leaderStatus);
        when(deltaFilesService.deltaFileStats()).thenReturn(createDeltaFileStats(50L, 10L, 5L));
        when(deltaFilesService.countUnacknowledgedErrors()).thenReturn(2L);
        when(buildProperties.getVersion()).thenReturn("1.0.0");

        try {
            when(objectMapper.treeToValue(any(), eq(MonitorResult.class))).thenReturn(healthyStatus);
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of()); // No members configured

        AggregatedStats stats = memberMonitorService.getAggregatedStats();

        // Should only have leader stats
        assertEquals(50L, stats.totalInFlight());
        assertEquals(2L, stats.totalErrors());
        assertEquals(10L, stats.totalWarmQueue());
        assertEquals(5L, stats.totalColdQueue());
        assertEquals(1, stats.memberCount());
        assertEquals(1, stats.healthyCount());
        assertEquals(0, stats.unhealthyCount());
    }

    private MonitorResult createHealthyMonitorResult() {
        return new MonitorResult(0, "green", "Healthy", List.of(), OffsetDateTime.now());
    }

    private MonitorResult createDegradedMonitorResult() {
        return new MonitorResult(1, "yellow", "Degraded", List.of(), OffsetDateTime.now());
    }

    private DeltaFileStats createDeltaFileStats(Long inFlight, Long warm, Long cold) {
        DeltaFileStats stats = new DeltaFileStats();
        stats.setInFlightCount(inFlight);
        stats.setWarmQueuedCount(warm);
        stats.setColdQueuedCount(cold);
        stats.setPausedCount(0L);
        return stats;
    }

    private MemberStatus createMemberStatus(String name, ConnectionState state, MonitorResult status,
                                            Long inFlight, Long errors, Long warm, Long cold) {
        return new MemberStatus(
                name,
                "http://" + name,
                List.of(),
                false,
                status,
                errors,
                inFlight,
                warm,
                cold,
                0L,  // pausedCount
                null,  // oldestInFlightCreated
                null,
                null,
                null,
                OffsetDateTime.now(),
                state,
                null,
                "1.0.0"
        );
    }
}
