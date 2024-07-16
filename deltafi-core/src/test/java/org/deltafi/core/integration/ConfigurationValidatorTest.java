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
package org.deltafi.core.integration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import org.deltafi.core.integration.config.Configuration;
import org.deltafi.core.plugin.PluginRegistryService;
import org.deltafi.core.services.RestDataSourceService;
import org.deltafi.core.services.EgressFlowService;
import org.deltafi.core.services.TransformFlowService;
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

@ExtendWith(MockitoExtension.class)
class ConfigurationValidatorTest {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(new JavaTimeModule());

    private final RestDataSourceService restDataSourceService;
    private final TransformFlowService transformFlowService;
    private final EgressFlowService egressFlowService;
    private final PluginRegistryService pluginRegistryService;

    private final ConfigurationValidator configurationValidator;

    ConfigurationValidatorTest(@Mock RestDataSourceService restDataSourceService,
                               @Mock TransformFlowService transformFlowService,
                               @Mock EgressFlowService egressFlowService,
                               @Mock PluginRegistryService pluginRegistryService) {
        this.restDataSourceService = restDataSourceService;
        this.transformFlowService = transformFlowService;
        this.egressFlowService = egressFlowService;
        this.pluginRegistryService = pluginRegistryService;

        this.configurationValidator = new ConfigurationValidator(
                this.restDataSourceService,
                this.transformFlowService,
                this.egressFlowService,
                this.pluginRegistryService);
    }

    @Test
    @SneakyThrows
    void testConfigCheck() {
        Configuration c = readConfig("config-binary.yaml");

        Mockito.when(pluginRegistryService.getPlugins()).thenReturn(new ArrayList<>());
        Mockito.when(restDataSourceService.hasFlow("unarchive-passthrough-rest-data-source")).thenReturn(false);
        Mockito.when(transformFlowService.hasFlow("unarchive-passthrough-transform")).thenReturn(false);
        Mockito.when(transformFlowService.hasFlow("passthrough-transform")).thenReturn(false);
        Mockito.when(egressFlowService.hasFlow("passthrough-egress")).thenReturn(false);

        List<String> actualErrors = configurationValidator.validateConfig(c);

        List<String> expectedErrors = List.of(
                "Plugin not found: org.deltafi:deltafi-core-actions:*",
                "Plugin not found: org.deltafi.testjig:deltafi-testjig:*",
                "Flow does not exist (dataSource): unarchive-passthrough-rest-data-source",
                "Flow does not exist (transformation): unarchive-passthrough-transform",
                "Flow does not exist (transformation): passthrough-transform",
                "Flow does not exist (egress): passthrough-egress"
        );
        assertEquals(expectedErrors, actualErrors);
    }

    Configuration readConfig(String file) throws IOException {
        byte[] bytes = new ClassPathResource("/integration/" + file).getInputStream().readAllBytes();
        return YAML_MAPPER.readValue(bytes, Configuration.class);
    }
}
