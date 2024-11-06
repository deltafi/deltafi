/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.gradle.plugin;

import lombok.Setter;
import org.deltafi.common.rules.RuleEvaluator;
import org.deltafi.common.rules.RuleValidator;
import org.deltafi.common.types.FlowPlan;
import org.deltafi.common.types.Publisher;
import org.deltafi.common.types.Subscriber;
import org.deltafi.common.types.Variable;
import org.deltafi.common.types.integration.IntegrationTest;
import org.deltafi.common.util.ResourceMapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A Gradle plugin for validating Deltafi plugins.
 * <p>
 * This plugin should be added to the build.gradle file at the root of a project that is a Deltafi plugin.
 * It should be added to the plugins section as follows:
 * </p>
 * <pre>
 * plugins {
 *     id "org.deltafi.plugin" version "${deltafiVersion}"
 * }
 * </pre>
 * <p>
 * The plugin may be configured by adding the following:
 * </p>
 * <pre>
 * deltafiPlugin {
 *   flowsDir = "flows" (optional, defaults to "src/main/resources/flows")
 *   testsDir = "integration" (optional, defaults to "src/main/resources/integration")
 * }
 * </pre>
 */
public class PluginPlugin implements org.gradle.api.Plugin<Project> {

    public static class DeltafiPluginExtension {
        String flowsDir;
        String testsDir;
    }

    @Setter
    public static class CheckDeltafiPluginTask extends DefaultTask {
        private PluginPlugin.DeltafiPluginExtension deltafiPluginExtension;

        private RuleValidator ruleValidator = new RuleValidator(new RuleEvaluator());

        @TaskAction
        public void check() {
            File flowsDirectory = new File(getProject().getProjectDir(), deltafiPluginExtension.flowsDir);
            if (!flowsDirectory.exists()) {
                getLogger().warn("Flows directory ({}) does not exist. No flows will be installed.", flowsDirectory);
            } else {
                checkVariables(flowsDirectory);
                checkFlowPlans(flowsDirectory);
            }

            File testsDirectory = new File(getProject().getProjectDir(), deltafiPluginExtension.testsDir);
            if (!testsDirectory.exists()) {
                getLogger().warn("Tests directory ({}) does not exist. No tests will be installed.", testsDirectory);
            } else {
                checkTests(testsDirectory);
            }
        }

        private void checkVariables(File flowsDirectory) {
            File variablesFile = findVariablesFile(flowsDirectory);
            if (variablesFile == null) {
                getLogger().info("No flow variables have been defined");
                return;
            }

            try {
                ResourceMapper.readValues(new FileSystemResource(variablesFile), Variable.class);
            } catch (IOException e) {
                throw new GradleException("Unable to load variables: " + e.getMessage(), e);
            }
        }

        private File findVariablesFile(File flowsDirectory) {
            for (String extension : List.of(".json", ".yaml", ".yml", ".jsonl")) {
                File file = new File(flowsDirectory, "variables" + extension);
                if (file.exists()) {
                    return file;
                }
            }

            return null;
        }

        private void checkFlowPlans(File flowsDirectory) {
            File[] flowFiles = flowsDirectory.listFiles(this::isFlowPlan);

            if (flowFiles == null) {
                throw new GradleException(flowsDirectory + " is not a directory");
            }

            if (flowFiles.length == 0) {
                getLogger().warn("No flow plans exist in the flows directory ({})", flowsDirectory);
            }

            List<String> errors = new ArrayList<>();
            for (File flowFile : flowFiles) {
                try {
                    String errorMessage = validateConditions(ResourceMapper.readValue(flowFile, FlowPlan.class), flowFile.getName());
                    if (errorMessage != null) {
                        errors.add(errorMessage);
                    }
                } catch (IOException e) {
                    throw new GradleException("Unable to load flow plan (" + flowFile + "): " + e.getMessage(), e);
                }
            }
            if (!errors.isEmpty()) {
                throw new GradleException("Invalid flow plan conditions found:\n" + String.join(";\n", errors));
            }
        }

        // consider any json or yaml file a flow plan unless it starts with variables
        private boolean isFlowPlan(File file) {
            String name = file.getName();
            return (name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml")) && !name.startsWith("variables.");
        }

        private String validateConditions(FlowPlan flowPlan, String fileName) {
            List<String> errors = new ArrayList<>();
            if (flowPlan instanceof Subscriber subscriber) {
                errors.addAll(ruleValidator.validateSubscriber(subscriber));
            }
            if (flowPlan instanceof Publisher publisher) {
                errors.addAll(ruleValidator.validatePublisher(publisher));
            }

            return !errors.isEmpty() ? "Errors in flow plan named `" + flowPlan.getName() + "` (file: " + fileName + "): " + String.join("; ", errors) : null;
        }

        private void checkTests(File testsDirectory) {
            File[] testFiles = testsDirectory.listFiles(this::isTestFile);

            if (testFiles == null) {
                throw new GradleException(testsDirectory + " is not a directory");
            }

            if (testFiles.length == 0) {
                getLogger().warn("No tests exist in the tests directory ({})", testsDirectory);
            }

            for (File testFile : testFiles) {
                try {
                    ResourceMapper.readValue(testFile, IntegrationTest.class);
                } catch (IOException e) {
                    throw new GradleException("Unable to load test (" + testFile + "): " + e.getMessage(), e);
                }
            }
        }

        private boolean isTestFile(File file) {
            String name = file.getName();
            return (name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml"));
        }
    }

    @Override
    public void apply(Project project) {
        DeltafiPluginExtension extension =
                project.getExtensions().create("deltafiPlugin", DeltafiPluginExtension.class);

        if (extension.flowsDir == null) {
            extension.flowsDir = "src/main/resources/flows";
        }

        if (extension.testsDir == null) {
            extension.testsDir = "src/main/resources/integration";
        }

        project.getTasks().register("checkDeltafiPlugin", CheckDeltafiPluginTask.class,
                task -> task.setDeltafiPluginExtension(extension));
    }
}
