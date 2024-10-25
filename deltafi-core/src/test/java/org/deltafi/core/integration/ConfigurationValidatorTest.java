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
package org.deltafi.core.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import org.deltafi.common.types.Plugin;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.integration.IntegrationTest;
import org.deltafi.core.services.DataSinkService;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.services.RestDataSourceService;
import org.deltafi.core.services.TransformFlowService;
import org.deltafi.core.types.PluginEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ConfigurationValidatorTest {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());

    private final RestDataSourceService restDataSourceService;
    private final TransformFlowService transformFlowService;
    private final DataSinkService dataSinkService;
    private final PluginService pluginService;

    private final ConfigurationValidator configurationValidator;

    ConfigurationValidatorTest(@Mock RestDataSourceService restDataSourceService,
                               @Mock TransformFlowService transformFlowService,
                               @Mock DataSinkService dataSinkService,
                               @Mock PluginService pluginService) {
        this.restDataSourceService = restDataSourceService;
        this.transformFlowService = transformFlowService;
        this.dataSinkService = dataSinkService;
        this.pluginService = pluginService;

        this.configurationValidator = new ConfigurationValidator(
                this.restDataSourceService,
                this.transformFlowService,
                this.dataSinkService,
                this.pluginService);
    }

    @Test
    @SneakyThrows
    void testPreSaveCheckFails() {
        IntegrationTest testCase = readYamlTestFile("config-invalid.yaml");

        List<String> actualErrors = configurationValidator.preSaveCheck(testCase);

        List<String> expectedErrors = List.of(
                "Test configuration must specify a 'description'",
                "Invalid 'timeout'; must be a valid Duration",
                "Test configuration is missing 'inputs''"
        );
        assertEquals(expectedErrors, actualErrors);
    }

    @Test
    @SneakyThrows
    void testPreSaveCheckSuccess() {
        IntegrationTest testCase = readYamlTestFile("config-binary.yaml");
        List<String> actualErrors = configurationValidator.preSaveCheck(testCase);
        assertTrue(actualErrors.isEmpty());
    }

    @Test
    @SneakyThrows
    void testValidateToStartFails() {
        IntegrationTest testCase = readYamlTestFile("config-binary.yaml");

        Mockito.when(pluginService.getPlugins()).thenReturn(new ArrayList<>());
        Mockito.when(restDataSourceService.hasFlow("unarchive-passthrough-rest-data-source")).thenReturn(false);
        Mockito.when(transformFlowService.hasFlow("unarchive-passthrough-transform")).thenReturn(false);
        Mockito.when(transformFlowService.hasFlow("passthrough-transform")).thenReturn(false);
        Mockito.when(dataSinkService.hasFlow("passthrough-egress")).thenReturn(false);

        List<String> actualErrors = configurationValidator.validateToStart(testCase);

        List<String> expectedErrors = List.of(
                "Plugin not found: org.deltafi:deltafi-core-actions:*",
                "Plugin not found: org.deltafi.testjig:deltafi-testjig:*",
                "Flow does not exist (dataSource): unarchive-passthrough-rest-data-source",
                "Flow does not exist (transformation): unarchive-passthrough-transform",
                "Flow does not exist (transformation): passthrough-transform",
                "Flow does not exist (dataSink): passthrough-egress"
        );
        assertEquals(expectedErrors, actualErrors);
    }

    @Test
    @SneakyThrows
    void testValidateToStartSuccess() {
        IntegrationTest testCase = readYamlTestFile("config-binary.yaml");

        Mockito.when(pluginService.getPlugins()).thenReturn(getPlugins());

        Mockito.when(restDataSourceService.hasFlow("unarchive-passthrough-rest-data-source")).thenReturn(true);
        Mockito.when(restDataSourceService.hasRunningFlow("unarchive-passthrough-rest-data-source")).thenReturn(true);

        Mockito.when(transformFlowService.hasFlow("unarchive-passthrough-transform")).thenReturn(true);
        Mockito.when(transformFlowService.hasRunningFlow("unarchive-passthrough-transform")).thenReturn(true);

        Mockito.when(transformFlowService.hasFlow("passthrough-transform")).thenReturn(true);
        Mockito.when(transformFlowService.hasRunningFlow("passthrough-transform")).thenReturn(true);

        Mockito.when(dataSinkService.hasFlow("passthrough-egress")).thenReturn(true);
        Mockito.when(dataSinkService.hasRunningFlow("passthrough-egress")).thenReturn(true);

        List<String> actualErrors = configurationValidator.validateToStart(testCase);
        assertTrue(actualErrors.isEmpty());
    }

    private List<PluginEntity> getPlugins() {
        Plugin p1 = new Plugin();
        p1.setPluginCoordinates(new PluginCoordinates("org.deltafi", "deltafi-core-actions", "1.0"));

        Plugin p2 = new Plugin();
        p2.setPluginCoordinates(new PluginCoordinates("org.deltafi.testjig", "deltafi-testjig", "1.0"));

        return List.of(new PluginEntity(p1), new PluginEntity(p2));
    }

    IntegrationTest readYamlTestFile(String file) throws IOException {
        byte[] bytes = new ClassPathResource("/integration/" + file).getInputStream().readAllBytes();
        return YAML_MAPPER.readValue(bytes, IntegrationTest.class);
    }
}
