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
package org.deltafi.core.converters;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.resource.Resource;
import org.deltafi.common.types.DefaultBehavior;
import org.deltafi.common.types.DefaultRule;
import org.deltafi.common.types.MatchingPolicy;
import org.deltafi.common.types.PublishRules;
import org.deltafi.common.types.Rule;
import org.deltafi.common.types.TimedIngressActionConfiguration;
import org.deltafi.common.types.TimedIngressFlowPlan;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.types.TimedIngressFlow;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class TimedIngressFlowPlanConverterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .registerModule(new JavaTimeModule());
    final TimedIngressFlowPlanConverter timedIngressFlowPlanConverter = new TimedIngressFlowPlanConverter();

    @Test
    void testConverter() throws IOException {
        TimedIngressFlowPlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-timedIngress-flowplan-test.json"), TimedIngressFlowPlan.class);
        TimedIngressFlow timedIngressFlow = timedIngressFlowPlanConverter.convert(flowPlan, Collections.emptyList());

        PublishRules publishRules = new PublishRules();
        publishRules.setMatchingPolicy(MatchingPolicy.FIRST_MATCHING);
        publishRules.setDefaultRule(new DefaultRule(DefaultBehavior.PUBLISH, "test"));
        publishRules.setRules(List.of(new Rule(Set.of("passthrough", "second"), "metadata['a'] == '1'")));
        assertThat(timedIngressFlow.getName()).isEqualTo("smoke-test-ingress");
        assertThat(timedIngressFlow.getTimedIngressAction()).isEqualTo(expectedTimedIngressAction());
        assertThat(timedIngressFlow.getCronSchedule()).isEqualTo("*/5 * * * * *");
        assertThat(timedIngressFlow.getFlowStatus().getState()).isEqualTo(FlowState.STOPPED);
        assertThat(timedIngressFlow.getFlowStatus().getTestMode()).isFalse();
        assertThat(timedIngressFlow.getPublishRules()).isEqualTo(publishRules);
    }

    @Test
    void testUnresolvedPlaceholder() throws IOException {
        TimedIngressFlowPlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-timedIngress-flowplan-unresolved-test.json"), TimedIngressFlowPlan.class);
        TimedIngressFlow timedIngressFlow = timedIngressFlowPlanConverter.convert(flowPlan, Collections.emptyList());

        assertThat(timedIngressFlow.getFlowStatus().getState()).isEqualTo(FlowState.INVALID);
        FlowConfigError expected = FlowConfigError.newBuilder()
                .configName("SmokeTestIngressAction")
                .errorType(FlowErrorType.UNRESOLVED_VARIABLE).message("Could not find a variable named 'smokeMetadataValue' used in value \"${smokeMetadataValue}\"").build();

        assertThat(timedIngressFlow.getFlowStatus().getErrors()).hasSize(1).contains(expected);
    }

    TimedIngressActionConfiguration expectedTimedIngressAction() {
        TimedIngressActionConfiguration timedIngressActionConfiguration = new TimedIngressActionConfiguration("SmokeTestIngressAction", "org.deltafi.core.action.SmokeTestIngressAction");
        timedIngressActionConfiguration.setInternalParameters(Map.of("metadata", Map.of("smoke", "test")));
        timedIngressActionConfiguration.setParameters(Map.of("metadata", Map.of("smoke", "test")));
        return timedIngressActionConfiguration;
    }
}