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
package org.deltafi.core.plugin.generator;

import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.deltafi.common.types.ActionType;
import org.deltafi.core.plugin.generator.flows.EgressFlowPlanGenerator;
import org.deltafi.core.plugin.generator.flows.EnrichFlowPlanGenerator;
import org.deltafi.core.plugin.generator.flows.FlowPlanGeneratorService;
import org.deltafi.core.plugin.generator.flows.NormalizeFlowPlanGenerator;
import org.deltafi.core.plugin.generator.flows.TransformFlowPlanGenerator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.FileSystemUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@SpringBootTest(classes = {JavaPluginGenerator.class, FlowPlanGeneratorService.class, NormalizeFlowPlanGenerator.class,
        TransformFlowPlanGenerator.class, EnrichFlowPlanGenerator.class, EgressFlowPlanGenerator.class, ProjectInfoAutoConfiguration.class})
class JavaPluginGeneratorTest {

    @Autowired
    JavaPluginGenerator javaPluginGenerator;

    @Test
    void testGeneratePlugin() throws IOException {
        ByteArrayOutputStream outputStream = javaPluginGenerator.generateProject(normalizationActions());
        Assertions.assertThat(outputStream.toByteArray()).isNotEmpty();
    }

    @Test
    @Disabled("Enable this test to write plugin source code out to a tmp location")
    void writePluginFiles() throws IOException {
        final Path outputDirectory = Path.of("/tmp/deltafi-generated/plugins");
        FileSystemUtils.deleteRecursively(outputDirectory);
        Files.createDirectories(outputDirectory);
        writePluginToDir(transformActions(), outputDirectory);
        writePluginToDir(normalizationActions(), outputDirectory);
        writePluginToDir(transformActionsWithHttpEgress(), outputDirectory);
        writePluginToDir(transformFlowEgressOnly(), outputDirectory);
    }

    private void writePluginToDir(PluginGeneratorInput pluginGeneratorInput, Path outputDirectory) {
        String fileName = pluginGeneratorInput.getArtifactId() + ".zip";
        File outFile = Path.of(outputDirectory.toString(), fileName).toFile();
        System.out.printf("Writing files to %s%n", outFile);
        try(OutputStream outputStream = new FileOutputStream(outFile)) {
            ByteArrayOutputStream pluginStream = javaPluginGenerator.generateProject(pluginGeneratorInput);
            pluginStream.writeTo(outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Includes a transform and egress action which should be used in the transform flow
    PluginGeneratorInput transformActions() {
        PluginGeneratorInput pluginGeneratorInput = new PluginGeneratorInput();
        pluginGeneratorInput.setGroupId("org.deltafi.sample.transform");
        pluginGeneratorInput.setArtifactId("MyTransformApp");
        pluginGeneratorInput.setPluginLanguage(PluginLanguage.JAVA);
        pluginGeneratorInput.setDescription("Set of sample DeltaFi Transform Actions");
        pluginGeneratorInput.setActions(Set.of(buildActionGeneratorInput(ActionType.TRANSFORM), buildActionGeneratorInput(ActionType.EGRESS)));
        return pluginGeneratorInput;
    }

    // Includes one of each action type, all of which should be used in the flows
    PluginGeneratorInput normalizationActions() {
        PluginGeneratorInput pluginGeneratorInput = new PluginGeneratorInput();
        pluginGeneratorInput.setGroupId("org.deltafi.sample.normalize");
        pluginGeneratorInput.setArtifactId("MyNormalizeApp");
        pluginGeneratorInput.setPluginLanguage(PluginLanguage.JAVA);
        pluginGeneratorInput.setDescription("Set of sample DeltaFi Normalization Actions");
        Set<ActionGeneratorInput> actionGeneratorInputs = Arrays.stream(ActionType.values())
                .filter(this::ignoreAction)
                .map(this::buildActionGeneratorInput)
                .collect(Collectors.toSet());
        pluginGeneratorInput.setActions(actionGeneratorInputs);
        return pluginGeneratorInput;
    }

    PluginGeneratorInput transformActionsWithHttpEgress() {
        PluginGeneratorInput pluginGeneratorInput = new PluginGeneratorInput();
        pluginGeneratorInput.setGroupId("org.deltafi.sample.transform");
        pluginGeneratorInput.setArtifactId("MyTransformAndEgressApp");
        pluginGeneratorInput.setPluginLanguage(PluginLanguage.JAVA);
        pluginGeneratorInput.setDescription("Set of sample DeltaFi Transform Actions");
        pluginGeneratorInput.setActions(Set.of(buildActionGeneratorInput(ActionType.TRANSFORM)));
        return pluginGeneratorInput;
    }

    PluginGeneratorInput transformFlowEgressOnly() {
        PluginGeneratorInput pluginGeneratorInput = new PluginGeneratorInput();
        pluginGeneratorInput.setGroupId("org.deltafi.sample.transform");
        pluginGeneratorInput.setArtifactId("EgressOnly");
        pluginGeneratorInput.setPluginLanguage(PluginLanguage.JAVA);
        pluginGeneratorInput.setDescription("Set of sample DeltaFi Transform Actions");
        pluginGeneratorInput.setActions(Set.of());
        return pluginGeneratorInput;
    }

    ActionGeneratorInput buildActionGeneratorInput(ActionType actionType) {
        String lowerCaseName = actionType.name().toLowerCase(Locale.ROOT);
        String actionTypeName = StringUtils.capitalize(lowerCaseName);
        String actionName = "My%sAction".formatted(actionTypeName);
        ActionGeneratorInput actionGeneratorInput = new ActionGeneratorInput(actionName, actionType, "Sample %s action".formatted(lowerCaseName));
        actionGeneratorInput.setParameterClassName("My%sParameters".formatted(actionTypeName));
        return actionGeneratorInput;
    }

    boolean ignoreAction(ActionType actionType) {
        return actionType != ActionType.TIMED_INGRESS && actionType != ActionType.INGRESS && actionType != ActionType.UNKNOWN && actionType != ActionType.PUBLISH;
    }
}