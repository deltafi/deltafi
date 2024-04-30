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
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.types.TestResult;
import org.deltafi.core.integration.config.Configuration;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.services.IngressService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
class IntegrationServiceTest {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(new JavaTimeModule());

    private final IngressService ingressService;
    private final ConfigurationValidator configurationValidator;
    private final DeltaFilesService deltaFilesService;
    private final ContentStorageService contentStorageService;
    private final TestResultRepo testResultRepo;

    private final IntegrationService integrationService;

    IntegrationServiceTest(@Mock IngressService ingressService,
                           @Mock ConfigurationValidator configurationValidator,
                           @Mock DeltaFilesService deltaFilesService,
                           @Mock ContentStorageService contentStorageService,
                           @Mock TestResultRepo testResultRepo) {
        this.ingressService = ingressService;
        this.configurationValidator = configurationValidator;
        this.deltaFilesService = deltaFilesService;
        this.contentStorageService = contentStorageService;
        this.testResultRepo = testResultRepo;

        this.integrationService = new IntegrationService(
                this.ingressService,
                this.configurationValidator,
                this.deltaFilesService,
                this.contentStorageService,
                this.testResultRepo);
    }

    @Test
    @SneakyThrows
    void testBinaryInput() {
        Configuration c = readConfig("/integration/config-binary.yaml");
        assertEquals(2, c.getPlugins().size());
        assertEquals(1, c.getDataSources().size());
        assertEquals(2, c.getTransformationFlows().size());
        assertEquals(1, c.getEgressFlows().size());

        assertEquals("unarchive-passthrough-rest-data-source", c.getInput().getFlow());

        assertEquals(3, c.getExpectedDeltaFile().getChildCount());

        List<String> mockErrors = new ArrayList<>();
        mockErrors.add("bad plugin");

        Mockito.when(configurationValidator.validateConfig(c)).thenReturn(mockErrors);
        TestResult r = integrationService.runTest(c);
        assertFalse(r.getErrors().isEmpty());
    }

    @Test
    @SneakyThrows
    void testPlainTextInput() {
        Configuration c = readConfig("/integration/config-text.yaml");
        assertEquals(2, c.getPlugins().size());
        assertEquals(1, c.getDataSources().size());
        assertEquals(2, c.getTransformationFlows().size());
        assertEquals(1, c.getEgressFlows().size());

        assertEquals("unarchive-passthrough-rest-data-source", c.getInput().getFlow());

        assertEquals(3, c.getExpectedDeltaFile().getChildCount());

        List<String> mockErrors = new ArrayList<>();
        mockErrors.add("bad plugin");

        Mockito.when(configurationValidator.validateConfig(c)).thenReturn(mockErrors);
        TestResult r = integrationService.runTest(c);
        assertFalse(r.getErrors().isEmpty());
    }

    Configuration readConfig(String path) throws IOException {
        byte[] theBytes = IntegrationServiceTest.class.getResourceAsStream(path).readAllBytes();
        String configYaml = new String(theBytes);
        Configuration config = YAML_MAPPER.readValue(configYaml, Configuration.class);
        return config;
    }
}
