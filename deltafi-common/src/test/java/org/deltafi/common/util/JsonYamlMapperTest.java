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
package org.deltafi.common.util;

import tools.jackson.core.type.TypeReference;
import org.assertj.core.api.Assertions;
import org.deltafi.common.types.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

class JsonYamlMapperTest {

    @Test
    void readTransformFlowPlan() throws IOException {
        TransformFlowPlan fromJson = readClasspath("test-flows/smoke-test-transform.json", TransformFlowPlan.class);
        TransformFlowPlan fromJsonTypRef;
        try (InputStream in = classpath("test-flows/smoke-test-transform.json")) {
            fromJsonTypRef = JsonYamlMapper.readValue(in, "smoke-test-transform.json", new TypeReference<>() {});
        }
        TransformFlowPlan fromYaml = readClasspath("test-flows/smoke-test-transform.yaml", TransformFlowPlan.class);

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
        List<Variable> variables = readClasspathList("test-flows/variables.yaml", Variable.class);
        List<Variable> variablesFromJson = readClasspathList("test-flows/variables.json", Variable.class);
        List<Variable> variableDocs = readClasspathList("test-flows/variablesList.yaml", Variable.class);
        List<Variable> variablesFromJsonl = readClasspathList("test-flows/variablesList.jsonl", Variable.class);

        Assertions.assertThat(variables).hasSize(4).isEqualTo(variableDocs).isEqualTo(variablesFromJson).isEqualTo(variablesFromJsonl);
        Variable first = variables.getFirst();
        Assertions.assertThat(first.getName()).isEqualTo("passthroughEgressUrl");
        Assertions.assertThat(first.getDescription()).isEqualTo("The URL to post the DeltaFile to");
        Assertions.assertThat(first.getDataType()).isEqualTo(VariableDataType.STRING);
        Assertions.assertThat(first.isRequired()).isTrue();
        Assertions.assertThat(first.getDefaultValue()).isEqualTo("http://deltafi-egress-sink-service");
    }

    private static InputStream classpath(String name) {
        return JsonYamlMapperTest.class.getClassLoader().getResourceAsStream(name);
    }

    private static String filename(String classpathName) {
        int slash = classpathName.lastIndexOf('/');
        return slash < 0 ? classpathName : classpathName.substring(slash + 1);
    }

    private static <T> T readClasspath(String name, Class<T> type) throws IOException {
        try (InputStream in = classpath(name)) {
            return JsonYamlMapper.readValue(in, filename(name), type);
        }
    }

    private static <T> List<T> readClasspathList(String name, Class<T> type) throws IOException {
        try (InputStream in = classpath(name)) {
            return JsonYamlMapper.readValues(in, filename(name), type);
        }
    }
}
