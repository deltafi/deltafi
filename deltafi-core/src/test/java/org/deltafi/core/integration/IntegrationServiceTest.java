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
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.TestStatus;
import org.deltafi.common.types.integration.IntegrationTest;
import org.deltafi.common.types.integration.TestCaseIngress;
import org.deltafi.core.services.IngressService;
import org.deltafi.core.types.IngressResult;
import org.deltafi.core.types.integration.TestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class IntegrationServiceTest {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());

    @Mock
    private IngressService ingressService;

    @Mock
    private ConfigurationValidator configurationValidator;

    @Mock
    private TestResultRepo testResultRepo;

    @InjectMocks
    private IntegrationService integrationService;

    @Test
    @SneakyThrows
    void testConvertWithBinary() {
        // More like a test for IntegrationDataFetcher.yamlToConfiguration()
        IntegrationTest testCase = readYamlTestFile("/config-binary.yaml");
        assertEquals(2, testCase.getPlugins().size());
        assertEquals(1, testCase.getDataSources().size());
        assertEquals(2, testCase.getTransformationFlows().size());
        assertEquals(1, testCase.getDataSinks().size());

        String data = "H4sIANXJomUAA+3TsQrCMBCA4cw+RR5ANBdzcXBSn6StS0FENMXXV1uhTkqH" +
                "VIr/t9yQwN3y16dzk5aFyck9xBieU9bq3mdHopGg6jQEdd44kSArY13Wq16a" +
                "ayou1prDrT6mD/++vU/UtuhtZr++BmOr2/7LrDsG9e9j27/39D+GXdmb0//f" +
                "6fqvsu4Y1L9K17/Q/xj2VW9B/wAAAAAAAAAAAAAwWXcwyxBtACgAAA==";

        TestCaseIngress ingress1 = TestCaseIngress.builder()
                .flow("unarchive-passthrough-rest-data-source")
                .ingressFileName("three-files.tar.gz")
                .base64Encoded(true)
                .data(data)
                .metadata(List.of(
                        new KeyValue("KEY1", "VALUE1"),
                        new KeyValue("KEY2", "VALUE2")))
                .build();

        assertEquals(ingress1, testCase.getInputs().getFirst());

        assertEquals(Map.of("KEY1", "VALUE1", "KEY2", "VALUE2"),
                ingress1.metadataToMap());

        assertEquals("unarchive-passthrough-rest-data-source", testCase.getInputs().getFirst().getFlow());
        assertEquals(3, testCase.getExpectedDeltaFiles().getFirst().getChildCount());
        assertEquals(Map.of("THIS", "THAT"), testCase.getExpectedDeltaFiles().getFirst().annotationsToMap());

        assertEquals(Map.of("KEY3", "VALUE3", "KEY4", "VALUE4"),
                testCase.getExpectedDeltaFiles().getFirst().getChildren()
                        .getFirst().getExpectedFlows().get(1).metadataToMap());
    }

    @Test
    @SneakyThrows
    void testConvertWithText() {
        // More like a test for IntegrationDataFetcher.yamlToConfiguration()
        IntegrationTest testCase = readYamlTestFile("/config-text.yaml");
        assertEquals(2, testCase.getPlugins().size());
        assertEquals(1, testCase.getDataSources().size());
        assertEquals(2, testCase.getTransformationFlows().size());
        assertEquals(1, testCase.getDataSinks().size());

        TestCaseIngress ingress1 = TestCaseIngress.builder()
                .flow("unarchive-passthrough-rest-data-source")
                .ingressFileName("file-1.txt")
                .base64Encoded(false)
                .data("Here is some\ntext on two lines")
                .build();

        TestCaseIngress ingress2 = TestCaseIngress.builder()
                .flow("unarchive-passthrough-rest-data-source")
                .ingressFileName("file-2.txt")
                .base64Encoded(false)
                .data("Single line of text content")
                .build();

        assertEquals(ingress1, testCase.getInputs().getFirst());
        assertEquals(ingress2, testCase.getInputs().get(1));

        assertEquals(3, testCase.getExpectedDeltaFiles().get(0).getChildCount());
        assertEquals(1, testCase.getExpectedDeltaFiles().get(1).getChildCount());
    }

    @Test
    @SneakyThrows
    void testRunTestSuccess() {
        IntegrationTest testCase = readYamlTestFile("/config-binary.yaml");

        Mockito.when(configurationValidator.validateToStart(testCase)).thenReturn(new ArrayList<>());
        Mockito.when(ingressService.ingress(
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.any(),
                        Mockito.any()))
                .thenReturn(List.of(
                        new IngressResult("unarchive-passthrough-rest-data-source", UUID.randomUUID(), null)));

        TestResult r = integrationService.runTest(testCase);
        assertEquals("plugin1.test1", r.getTestName());
        assertFalse(r.getId().isEmpty());
        assertEquals(TestStatus.STARTED, r.getStatus());
        assertTrue(r.getErrors().isEmpty());
    }

    @Test
    @SneakyThrows
    void testRunTestFails() {
        IntegrationTest testCase = readYamlTestFile("/config-binary.yaml");

        Mockito.when(configurationValidator.validateToStart(testCase)).thenReturn(new ArrayList<>());
        Mockito.when(ingressService.ingress(
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.any(),
                        Mockito.any()))
                .thenReturn(Collections.emptyList());

        TestResult r = integrationService.runTest(testCase);
        assertEquals("plugin1.test1", r.getTestName());
        assertNull(r.getId());
        assertEquals(TestStatus.INVALID, r.getStatus());

        List<String> expectedErrors = List.of(
                "Failed to ingress"
        );
        assertEquals(expectedErrors, r.getErrors());
    }

    IntegrationTest readYamlTestFile(String file) throws IOException {
        byte[] bytes = new ClassPathResource("/integration/" + file).getInputStream().readAllBytes();
        return YAML_MAPPER.readValue(bytes, IntegrationTest.class);
    }
}
