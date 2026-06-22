/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.types;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Polymorphic serialization roundtrip tests for the {@link FlowPlan} hierarchy. Guards
 * against any Jackson 3 polymorphic-encoding regression in the {@code @JsonTypeInfo} +
 * {@code @JsonSubTypes} pair on {@code FlowPlan}.
 */
class FlowPlanPolymorphismTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("TransformFlowPlan roundtrips through abstract FlowPlan type")
    void transformFlowPlanRoundtrip() {
        TransformFlowPlan original = new TransformFlowPlan("my-transform", "a transform plan");
        original.setSubscribe(Set.of(new Rule("input-topic")));

        String json = mapper.writeValueAsString((FlowPlan) original);
        FlowPlan deserialized = mapper.readValue(json, FlowPlan.class);

        assertThat(deserialized).isInstanceOf(TransformFlowPlan.class).isEqualTo(original);
        assertThat(deserialized.getName()).isEqualTo("my-transform");
        assertThat(deserialized.getType()).isEqualTo(FlowType.TRANSFORM);
    }

    @Test
    @DisplayName("RestDataSourcePlan roundtrips through abstract FlowPlan type")
    void restDataSourcePlanRoundtrip() {
        RestDataSourcePlan original = new RestDataSourcePlan("my-rest-source", FlowType.REST_DATA_SOURCE, "a rest source", "my-topic");

        String json = mapper.writeValueAsString((FlowPlan) original);
        FlowPlan deserialized = mapper.readValue(json, FlowPlan.class);

        assertThat(deserialized).isInstanceOf(RestDataSourcePlan.class).isEqualTo(original);
        assertThat(deserialized.getName()).isEqualTo("my-rest-source");
        assertThat(deserialized.getType()).isEqualTo(FlowType.REST_DATA_SOURCE);
        assertThat(((RestDataSourcePlan) deserialized).getTopic()).isEqualTo("my-topic");
    }

    @Test
    @DisplayName("DataSinkPlan roundtrips through abstract FlowPlan type")
    void dataSinkPlanRoundtrip() {
        ActionConfiguration egressAction = new ActionConfiguration("MyEgress", ActionType.EGRESS, "org.deltafi.core.action.RestPostEgress");
        DataSinkPlan original = new DataSinkPlan("my-sink", "a sink plan", egressAction);
        original.setSubscribe(Set.of(new Rule("egress-topic")));

        String json = mapper.writeValueAsString((FlowPlan) original);
        FlowPlan deserialized = mapper.readValue(json, FlowPlan.class);

        assertThat(deserialized).isInstanceOf(DataSinkPlan.class).isEqualTo(original);
        assertThat(deserialized.getName()).isEqualTo("my-sink");
        assertThat(deserialized.getType()).isEqualTo(FlowType.DATA_SINK);
        assertThat(((DataSinkPlan) deserialized).getEgressAction().getName()).isEqualTo("MyEgress");
    }

    @Test
    @DisplayName("Polymorphic JSON encodes the type discriminator")
    void polymorphicJsonHasTypeDiscriminator() {
        TransformFlowPlan plan = new TransformFlowPlan("d", "d");
        plan.setSubscribe(Set.of(new Rule("t")));

        String json = mapper.writeValueAsString((FlowPlan) plan);

        assertThat(json).contains("\"type\":\"TRANSFORM\"");
    }
}
