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
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.TestResult;
import org.deltafi.common.types.TestStatus;
import org.deltafi.core.integration.config.Configuration;
import org.deltafi.core.security.NeedsPermission;

import java.util.ArrayList;
import java.util.List;

@DgsComponent
@RequiredArgsConstructor
public class IntegrationDataFetcher {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory()).setSerializationInclusion(JsonInclude.Include.NON_NULL).registerModule(new JavaTimeModule());
    private final IntegrationService integrationService;

    @DgsQuery
    @NeedsPermission.IntegrationTestView
    public TestResult getIntegrationTest(@InputArgument String id) {
        return integrationService.get(id);
    }

    @DgsQuery
    @NeedsPermission.IntegrationTestView
    public List<TestResult> getAllIntegrationTests() {
        return integrationService.getAll();
    }

    @DgsMutation
    @NeedsPermission.IntegrationTestDelete
    public boolean removeIntegrationTest(@InputArgument String id) {
        return integrationService.remove(id);
    }

    @DgsMutation
    @NeedsPermission.IntegrationTestLaunch
    public TestResult launchIntegrationTest(@InputArgument String configYaml) {
        List<String> errors = new ArrayList<>();
        try {
            Configuration config = YAML_MAPPER.readValue(configYaml, Configuration.class);
            return integrationService.runTest(config);
        } catch (Exception e) {
            errors.add("Unable to parse YAML: " + e.getMessage());
            //errors.add(configYaml);
        }
        return TestResult.builder()
                .status(TestStatus.INVALID)
                .errors(errors)
                .build();
    }
}
