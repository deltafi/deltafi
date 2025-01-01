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
package org.deltafi.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import org.assertj.core.api.Assertions;
import org.deltafi.common.types.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

class ResourceMapperTest {

    @Test
    void readTransformFlowPlan() throws IOException {
        TransformFlowPlan fromJson = ResourceMapper.readValue(new ClassPathResource("test-flows/smoke-test-transform.json"), TransformFlowPlan.class);
        TransformFlowPlan fromJsonTypRef = ResourceMapper.readValue(new ClassPathResource("test-flows/smoke-test-transform.json"), new TypeReference<>() {});
        TransformFlowPlan fromYaml = ResourceMapper.readValue(new ClassPathResource("test-flows/smoke-test-transform.yaml"), TransformFlowPlan.class);

        Assertions.assertThat(fromJson).isEqualTo(fromYaml).isEqualTo(fromJsonTypRef);

        Assertions.assertThat(fromYaml.getName()).isEqualTo("smoke-test-transform");
        Assertions.assertThat(fromYaml.getType()).isEqualTo(FlowType.TRANSFORM);
        Assertions.assertThat(fromYaml.getDescription()).isEqualTo("Test flow that passes data through unchanged");

        Assertions.assertThat(fromYaml.getSubscribe())
                .hasSize(1)
                .extracting(Rule::getTopic)
                .containsExactly("smoke-transform");

        Assertions.assertThat(fromYaml.getTransformActions())
                .hasSize(1)
                .satisfies(actionList -> {
                    ActionConfiguration action = actionList.getFirst();
                    Assertions.assertThat(action.getName()).isEqualTo("SmokeTransformAction");
                    Assertions.assertThat(action.getType()).isEqualTo("org.deltafi.core.action.delay.Delay");
                    Assertions.assertThat(action.getParameters()).containsEntry("minDelayMS", "${minDelayMS}")
                            .containsEntry("maxDelayMS", "${maxDelayMS}");
                });


        PublishRules publish = fromYaml.getPublish();
        Assertions.assertThat(publish.getMatchingPolicy()).isEqualTo(MatchingPolicy.FIRST_MATCHING);
        Assertions.assertThat(publish.getDefaultRule().getDefaultBehavior()).isEqualTo(DefaultBehavior.ERROR);
        Assertions.assertThat(publish.getRules())
                .hasSize(1)
                .extracting(Rule::getTopic)
                .containsExactly("smoke-egress");
    }

    @Test
    void readYamlList() throws IOException {
        // read a standard yaml and json lists
        List<Variable> variables = ResourceMapper.readValues(new ClassPathResource("test-flows/variables.yaml"), Variable.class);
        List<Variable> variablesFromJson = ResourceMapper.readValues(new ClassPathResource("test-flows/variables.json"), Variable.class);
        // read variables from a list of individual documents inside a single file
        List<Variable> variableDocs = ResourceMapper.readValues(new ClassPathResource("test-flows/variablesList.yaml"), Variable.class);
        List<Variable> variablesFromJsonl = ResourceMapper.readValues(new ClassPathResource("test-flows/variablesList.jsonl"), Variable.class);

        Assertions.assertThat(variables).hasSize(4).isEqualTo(variableDocs).isEqualTo(variablesFromJson).isEqualTo(variablesFromJsonl);
        Variable first = variables.getFirst();
        Assertions.assertThat(first.getName()).isEqualTo("passthroughEgressUrl");
        Assertions.assertThat(first.getDescription()).isEqualTo("The URL to post the DeltaFile to");
        Assertions.assertThat(first.getDataType()).isEqualTo(VariableDataType.STRING);
        Assertions.assertThat(first.isRequired()).isTrue();
        Assertions.assertThat(first.getDefaultValue()).isEqualTo("http://deltafi-egress-sink-service");
    }
}