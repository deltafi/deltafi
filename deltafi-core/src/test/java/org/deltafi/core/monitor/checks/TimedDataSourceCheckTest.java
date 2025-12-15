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
package org.deltafi.core.monitor.checks;

import org.deltafi.common.types.IngressStatus;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.services.TimedDataSourceService;
import org.deltafi.core.types.TimedDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimedDataSourceCheckTest {

    @Mock
    private TimedDataSourceService timedDataSourceService;

    private TimedDataSourceCheck timedDataSourceCheck;

    @BeforeEach
    void setUp() {
        timedDataSourceCheck = new TimedDataSourceCheck(timedDataSourceService);
    }

    @Test
    void check_healthyFlows_returnsGreen() {
        TimedDataSource healthy = timedDataSource("healthy", IngressStatus.HEALTHY, null);
        when(timedDataSourceService.getRunningFlows()).thenReturn(List.of(healthy));

        CheckResult result = timedDataSourceCheck.check();

        assertThat(result.code()).isEqualTo(CheckResult.CODE_GREEN);
        assertThat(result.message()).isEmpty();
    }

    @Test
    void check_unhealthyFlows_returnsYellowWithDetails() {
        TimedDataSource healthy = timedDataSource("healthy-source", IngressStatus.HEALTHY, null);
        TimedDataSource unhealthy = timedDataSource("sftp1", IngressStatus.UNHEALTHY, "Connection refused");
        when(timedDataSourceService.getRunningFlows()).thenReturn(List.of(healthy, unhealthy));

        CheckResult result = timedDataSourceCheck.check();

        assertThat(result.code()).isEqualTo(CheckResult.CODE_YELLOW);
        assertThat(result.message()).contains("Ingress Status is Unhealthy");
        assertThat(result.message()).contains("__sftp1__");
        assertThat(result.message()).contains("Connection refused");
        assertThat(result.message()).contains("/config/data-sources");
        assertThat(result.message()).doesNotContain("healthy-source");
    }

    @Test
    void check_unhealthyFlowWithNullMessage_handlesGracefully() {
        TimedDataSource unhealthy = timedDataSource("flow", IngressStatus.UNHEALTHY, null);
        when(timedDataSourceService.getRunningFlows()).thenReturn(List.of(unhealthy));

        CheckResult result = timedDataSourceCheck.check();

        assertThat(result.code()).isEqualTo(CheckResult.CODE_YELLOW);
        assertThat(result.message()).contains("__flow__");
        assertThat(result.message()).doesNotContain("null");
    }

    private TimedDataSource timedDataSource(String name, IngressStatus ingressStatus, String statusMessage) {
        TimedDataSource dataSource = new TimedDataSource();
        dataSource.setName(name);
        dataSource.setIngressStatus(ingressStatus);
        dataSource.setIngressStatusMessage(statusMessage);
        dataSource.setSourcePlugin(PluginCoordinates.builder()
                .groupId("org.deltafi")
                .artifactId("test-plugin")
                .version("1.0.0")
                .build());
        FlowStatus flowStatus = new FlowStatus();
        flowStatus.setState(FlowState.RUNNING);
        dataSource.setFlowStatus(flowStatus);
        return dataSource;
    }
}
