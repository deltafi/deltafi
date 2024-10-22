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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.TestStatus;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.integration.IntegrationTest;
import org.deltafi.core.types.integration.TestResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@DgsComponent
@RequiredArgsConstructor
public class IntegrationDataFetcher {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(new JavaTimeModule());

    private final IntegrationService integrationService;

    @DgsQuery
    @NeedsPermission.IntegrationTestView
    public IntegrationTest getIntegrationTest(@InputArgument String name) {
        Optional<IntegrationTest> result = integrationService.getIntegrationTest(name);
        return result.orElseThrow(() -> new DgsEntityNotFoundException("No integration test exists with the name " + name));

    }

    @DgsQuery
    @NeedsPermission.IntegrationTestView
    public TestResult getTestResult(@InputArgument String id) {
        Optional<TestResult> result = integrationService.getTestResult(id);
        return result.orElseThrow(() -> new DgsEntityNotFoundException("No test result exists with the id " + id));
    }

    @DgsQuery
    @NeedsPermission.IntegrationTestView
    public List<IntegrationTest> getIntegrationTests() {
        return integrationService.getAllTests();
    }

    @DgsQuery
    @NeedsPermission.IntegrationTestView
    public List<TestResult> getTestResults() {
        return integrationService.getAllResults();
    }

    @DgsMutation
    @NeedsPermission.IntegrationTestDelete
    public boolean removeIntegrationTest(@InputArgument String name) {
        return integrationService.removeTest(name);
    }

    @DgsMutation
    @NeedsPermission.IntegrationTestDelete
    public boolean removeTestResult(@InputArgument String id) {
        return integrationService.removeResult(id);
    }

    @DgsMutation
    @NeedsPermission.IntegrationTestUpdate
    public Result loadIntegrationTest(@InputArgument String configYaml) {
        List<String> errors = new ArrayList<>();
        try {
            IntegrationTest testCase = YAML_MAPPER.readValue(configYaml, IntegrationTest.class);
            return integrationService.save(testCase);
        } catch (Exception e) {
            errors.add("Unable to parse YAML: " + e.getMessage());
        }
        return Result.builder()
                .success(false)
                .errors(errors)
                .build();
    }

    @DgsMutation
    @NeedsPermission.IntegrationTestUpdate
    public Result saveIntegrationTest(@InputArgument IntegrationTest testCase) {
        return integrationService.save(testCase);
    }

    @DgsMutation
    @NeedsPermission.IntegrationTestUpdate
    public TestResult startIntegrationTest(@InputArgument String name) {
        Optional<IntegrationTest> integrationTest = integrationService.getIntegrationTest(name);
        if (integrationTest.isPresent()) {
            return integrationService.runTest(integrationTest.get());
        }

        return TestResult.builder()
                .status(TestStatus.INVALID)
                .errors(List.of("Unable to find test: " + name))
                .build();
    }
}
