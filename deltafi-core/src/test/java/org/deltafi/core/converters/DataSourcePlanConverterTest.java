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
import org.deltafi.common.types.OnErrorDataSourcePlan;
import org.deltafi.common.types.RestDataSourcePlan;
import org.deltafi.common.types.TimedDataSourcePlan;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.types.OnErrorDataSource;
import org.deltafi.core.types.RestDataSource;
import org.deltafi.core.types.TimedDataSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class DataSourcePlanConverterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .registerModule(new JavaTimeModule());
    final RestDataSourcePlanConverter restDataSourcePlanConverter = new RestDataSourcePlanConverter();
    final TimedDataSourcePlanConverter timedDataSourcePlanConverter = new TimedDataSourcePlanConverter();
    final OnErrorDataSourcePlanConverter onErrorDataSourcePlanConverter = new OnErrorDataSourcePlanConverter();

    @Test
    void testConverterRest() throws IOException {
        RestDataSourcePlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-datasource-plan-rest.json"), RestDataSourcePlan.class);
        RestDataSource restDataSource = restDataSourcePlanConverter.convert(flowPlan, Collections.emptyList());
        assertThat(restDataSource.getName()).isEqualTo("data-source-rest");
        assertThat(restDataSource.getMetadata()).isEmpty();
        assertThat(restDataSource.getAnnotationConfig().nothingConfigured()).isTrue();
    }

    @Test
    void testConverterRestWithAnnotations() throws IOException {
        RestDataSourcePlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-datasource-plan-rest-with-annot.json"), RestDataSourcePlan.class);
        RestDataSource restDataSource = restDataSourcePlanConverter.convert(flowPlan, Collections.emptyList());
        assertThat(restDataSource.getName()).isEqualTo("annotate-rest");
        Map<String, String> annotations = restDataSource.getAnnotationConfig().getAnnotations();
        assertThat(annotations).containsEntry("annot1", "Val1");
        assertThat(restDataSource.getMetadata()).isEmpty();
    }

    @Test
    void testConverterRestWithMetadata() throws IOException {
        RestDataSourcePlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-datasource-plan-rest-with-meta.json"), RestDataSourcePlan.class);
        RestDataSource restDataSource = restDataSourcePlanConverter.convert(flowPlan, Collections.emptyList());
        assertThat(restDataSource.getName()).isEqualTo("metadata-rest");
        Map<String, String> metadata = restDataSource.getMetadata();
        assertThat(metadata).containsEntry("keyX", "valueX");
        assertThat(metadata).containsEntry("keyY", "valueY");
        assertThat(restDataSource.getAnnotationConfig().nothingConfigured()).isTrue();
    }

    @Test
    void testTimedConverter() throws IOException {
        TimedDataSourcePlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-datasource-plan-test.json"), TimedDataSourcePlan.class);
        TimedDataSource timedDataSource = timedDataSourcePlanConverter.convert(flowPlan, Collections.emptyList());

        assertThat(timedDataSource.getName()).isEqualTo("smoke-test-ingress");
        assertThat(timedDataSource.getTimedIngressAction()).isEqualTo(expectedTimedIngressAction());
        assertThat(timedDataSource.getCronSchedule()).isEqualTo("*/5 * * * * *");
        assertThat(timedDataSource.getFlowStatus().getState()).isEqualTo(FlowState.STOPPED);
        assertThat(timedDataSource.getFlowStatus().getTestMode()).isFalse();
        assertThat(timedDataSource.getAnnotationConfig().nothingConfigured()).isTrue();
        assertThat(timedDataSource.getMetadata()).isEmpty();
    }

    @Test
    void testTimedConverterMetaAndAnnot() throws IOException {
        TimedDataSourcePlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-datasource-plan-test-with-extra.json"), TimedDataSourcePlan.class);
        TimedDataSource timedDataSource = timedDataSourcePlanConverter.convert(flowPlan, Collections.emptyList());

        assertThat(timedDataSource.getName()).isEqualTo("smoke-test-ingress");
        assertThat(timedDataSource.getTimedIngressAction()).isEqualTo(expectedTimedIngressAction());
        assertThat(timedDataSource.getCronSchedule()).isEqualTo("*/5 * * * * *");
        assertThat(timedDataSource.getFlowStatus().getState()).isEqualTo(FlowState.STOPPED);
        assertThat(timedDataSource.getFlowStatus().getTestMode()).isFalse();

        Map<String, String> metadata = timedDataSource.getMetadata();
        assertThat(metadata).containsEntry("keyX", "valueX");
        assertThat(metadata).containsEntry("keyY", "valueY");

        Map<String, String> annotations = timedDataSource.getAnnotationConfig().getAnnotations();
        assertThat(annotations).containsEntry("annot1", "Val1");
    }

    @Test
    void testUnresolvedPlaceholder() throws IOException {
        TimedDataSourcePlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-datasource-plan-unresolved-test.json"), TimedDataSourcePlan.class);
        TimedDataSource dataSource = timedDataSourcePlanConverter.convert(flowPlan, Collections.emptyList());

        assertThat(dataSource.getFlowStatus().getState()).isEqualTo(FlowState.STOPPED);
        assertThat(dataSource.getFlowStatus().getValid()).isFalse();
        FlowConfigError expected = FlowConfigError.newBuilder()
                .configName("SmokeTestIngressAction")
                .errorType(FlowErrorType.UNRESOLVED_VARIABLE).message("Could not find a variable named 'smokeMetadataValue' used in value \"${smokeMetadataValue}\"").build();

        assertThat(dataSource.getFlowStatus().getErrors()).hasSize(1).contains(expected);
    }

    @Test
    void testOnErrorConverter() throws IOException {
        OnErrorDataSourcePlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-datasource-plan-on-error.json"), OnErrorDataSourcePlan.class);
        OnErrorDataSource onErrorDataSource = onErrorDataSourcePlanConverter.convert(flowPlan, Collections.emptyList());

        assertThat(onErrorDataSource.getName()).isEqualTo("on-error-test");
        assertThat(onErrorDataSource.getErrorMessageRegex()).isEqualTo("Error: .*");
        assertThat(onErrorDataSource.getSourceFilters()).hasSize(4);
        assertThat(onErrorDataSource.getSourceFilters().get(0).getActionName()).isEqualTo("action1");
        assertThat(onErrorDataSource.getSourceFilters().get(1).getActionName()).isEqualTo("action2");
        assertThat(onErrorDataSource.getSourceFilters().get(2).getFlowName()).isEqualTo("transform1");
        assertThat(onErrorDataSource.getSourceFilters().get(3).getFlowName()).isEqualTo("sink1");
        assertThat(onErrorDataSource.getMetadataFilters()).hasSize(1);
        assertThat(onErrorDataSource.getMetadataFilters().getFirst().getKey()).isEqualTo("env");
        assertThat(onErrorDataSource.getMetadataFilters().getFirst().getValue()).isEqualTo("prod");
        assertThat(onErrorDataSource.getAnnotationFilters()).hasSize(1);
        assertThat(onErrorDataSource.getAnnotationFilters().getFirst().getKey()).isEqualTo("priority");
        assertThat(onErrorDataSource.getAnnotationFilters().getFirst().getValue()).isEqualTo("high");
        assertThat(onErrorDataSource.getIncludeSourceMetadataRegex()).containsExactly("source-.*");
        assertThat(onErrorDataSource.getIncludeSourceAnnotationsRegex()).containsExactly("annotation-.*");
        assertThat(onErrorDataSource.getAnnotationConfig().nothingConfigured()).isTrue();
        assertThat(onErrorDataSource.getMetadata()).isEmpty();
    }

    ActionConfiguration expectedTimedIngressAction() {
        ActionConfiguration ActionConfiguration = new ActionConfiguration("SmokeTestIngressAction", ActionType.TIMED_INGRESS, "org.deltafi.core.action.SmokeTestIngressAction");
        ActionConfiguration.setInternalParameters(Map.of("metadata", Map.of("smoke", "test")));
        ActionConfiguration.setParameters(Map.of("metadata", Map.of("smoke", "test")));
        return ActionConfiguration;
    }
}
