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
package org.deltafi.core.converters;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.resource.Resource;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.TimedDataSourcePlan;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.types.TimedDataSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class RestDataSourcePlanConverterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .registerModule(new JavaTimeModule());
    final TimedDataSourcePlanConverter timedDataSourcePlanConverter = new TimedDataSourcePlanConverter();

    @Test
    void testConverter() throws IOException {
        TimedDataSourcePlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-datasource-plan-test.json"), TimedDataSourcePlan.class);
        TimedDataSource timedDataSource = timedDataSourcePlanConverter.convert(flowPlan, Collections.emptyList());

        assertThat(timedDataSource.getName()).isEqualTo("smoke-test-ingress");
        assertThat(timedDataSource.getTimedIngressAction()).isEqualTo(expectedTimedIngressAction());
        assertThat(timedDataSource.getCronSchedule()).isEqualTo("*/5 * * * * *");
        assertThat(timedDataSource.getFlowStatus().getState()).isEqualTo(FlowState.STOPPED);
        assertThat(timedDataSource.getFlowStatus().getTestMode()).isFalse();
    }

    @Test
    void testUnresolvedPlaceholder() throws IOException {
        TimedDataSourcePlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-datasource-plan-unresolved-test.json"), TimedDataSourcePlan.class);
        TimedDataSource dataSource = timedDataSourcePlanConverter.convert(flowPlan, Collections.emptyList());

        assertThat(dataSource.getFlowStatus().getState()).isEqualTo(FlowState.INVALID);
        FlowConfigError expected = FlowConfigError.newBuilder()
                .configName("SmokeTestIngressAction")
                .errorType(FlowErrorType.UNRESOLVED_VARIABLE).message("Could not find a variable named 'smokeMetadataValue' used in value \"${smokeMetadataValue}\"").build();

        assertThat(dataSource.getFlowStatus().getErrors()).hasSize(1).contains(expected);
    }

    ActionConfiguration expectedTimedIngressAction() {
        ActionConfiguration ActionConfiguration = new ActionConfiguration("SmokeTestIngressAction", ActionType.TIMED_INGRESS, "org.deltafi.core.action.SmokeTestIngressAction");
        ActionConfiguration.setInternalParameters(Map.of("metadata", Map.of("smoke", "test")));
        ActionConfiguration.setParameters(Map.of("metadata", Map.of("smoke", "test")));
        return ActionConfiguration;
    }
}