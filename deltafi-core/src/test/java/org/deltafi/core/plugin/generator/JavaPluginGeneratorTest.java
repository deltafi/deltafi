/**
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
package org.deltafi.core.plugin.generator;

import org.assertj.core.api.Assertions;
import org.deltafi.common.types.ActionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

@SpringBootTest(classes = {JavaPluginGenerator.class, ProjectInfoAutoConfiguration.class})
class JavaPluginGeneratorTest {

    @Autowired
    JavaPluginGenerator javaPluginGenerator;

    @Test
    void testGeneratePlugin() throws IOException {
        PluginGeneratorInput pluginGeneratorInput = new PluginGeneratorInput();
        pluginGeneratorInput.setGroupId("org.deltafi.sample");
        pluginGeneratorInput.setArtifactId("MyApp");
        pluginGeneratorInput.setPluginLanguage(PluginLanguage.JAVA);
        pluginGeneratorInput.setDescription("Set of sample DeltaFi Actions");

        ActionGeneratorInput loadAction = new ActionGeneratorInput();
        loadAction.setDescription("Sample load action");
        loadAction.setClassName("SampleLoadAction");
        loadAction.setActionType(ActionType.LOAD);
        loadAction.setParameterClassName("SampleParameters");

        ActionGeneratorInput egressActions = new ActionGeneratorInput();
        egressActions.setDescription("Sample egress action");
        egressActions.setClassName("SampleEgressAction");
        egressActions.setActionType(ActionType.EGRESS);

        pluginGeneratorInput.setActions(Set.of(loadAction, egressActions));

        ByteArrayOutputStream outputStream = javaPluginGenerator.generateProject(pluginGeneratorInput);

        Assertions.assertThat(outputStream.toByteArray()).isNotEmpty();
    }
}