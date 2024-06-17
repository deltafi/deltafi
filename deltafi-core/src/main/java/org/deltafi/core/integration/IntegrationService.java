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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.TestResult;
import org.deltafi.common.types.TestStatus;
import org.deltafi.core.exceptions.IngressException;
import org.deltafi.core.exceptions.IngressMetadataException;
import org.deltafi.core.exceptions.IngressStorageException;
import org.deltafi.core.exceptions.IngressUnavailableException;
import org.deltafi.core.integration.config.Configuration;
import org.deltafi.core.integration.config.Input;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.services.IngressService;
import org.deltafi.core.types.IngressResult;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationService {
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String USERNAME = "itest";
    private static final String TEST_ID_KEY = "deltaFiIntTestId";
    private static final String TEST_PREFIX_MACRO = "XXX_TESTPREFIX_XXX";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IngressService ingressService;
    private final ConfigurationValidator configurationValidator;
    private final DeltaFilesService deltaFilesService;
    private final ContentStorageService contentStorageService;
    private final TestResultRepo testResultRepo;

    private boolean processIncomingRequests = true;
    private ExecutorService executor = Executors.newCachedThreadPool();

    @PreDestroy
    public void onShutdown() throws InterruptedException {
        processIncomingRequests = false;

        // give a grace period events to be assigned to executor threads
        Thread.sleep(100);

        if (executor != null) {
            executor.shutdown();
            boolean ignored = executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    public TestResult runTest(Configuration config) {
        String testId = UUID.randomUUID().toString();
        if (!processIncomingRequests) {
            return TestResult.builder()
                    .status(TestStatus.INVALID)
                    .errors(List.of("Service shutting down"))
                    .build();
        }

        List<String> errors = configurationValidator.validateConfig(config);
        OffsetDateTime startTime = OffsetDateTime.now();

        if (errors.isEmpty()) {
            errors.addAll(ingressAndEvaluate(testId, startTime, config));
        } else {
            errors.addFirst("Could not validate config");
        }

        TestResult testResult;
        if (errors.isEmpty()) {
            testResult = TestResult.builder()
                    .id(testId)
                    .status(TestStatus.STARTED)
                    .start(startTime)
                    .build();
        } else {
            testResult = TestResult.builder()
                    .status(TestStatus.INVALID)
                    .errors(errors)
                    .build();
        }
        return testResult;
    }

    private List<String> ingressAndEvaluate(String testId, OffsetDateTime startTime, Configuration config) {
        List<String> errors = new ArrayList<>();

        try {
            List<IngressResult> ingressResults = ingress(testId, config.getInputs());
            if (ingressResults.isEmpty()) {
                errors.add("Failed to ingress");
            } else {
                TestResult started = TestResult.builder()
                        .id(testId)
                        .status(TestStatus.STARTED)
                        .start(startTime)
                        .build();
                testResultRepo.save(started);
                evaluate(testId, ingressResults.getFirst(), config);
            }
        } catch (JsonProcessingException e) {
            errors.add("Failed to parse input metadata: " + e.getMessage());
        } catch (Exception e) {
            errors.add("Failed to ingress: " + e.getMessage());
        }

        return errors;
    }

    private List<IngressResult> ingress(String testId, List<Input> inputs) throws IngressUnavailableException, ObjectStorageException, IngressStorageException, IngressMetadataException, IngressException, InterruptedException, JsonProcessingException {
        List<IngressResult> results = new ArrayList<>();
        for (Input input : inputs) {
            String contentType = StringUtils.isNoneEmpty(input.getContentType()) ? input.getContentType() : DEFAULT_CONTENT_TYPE;

            results.addAll(ingressService.ingress(
                    input.getFlow(),
                    input.getIngressFileName(),
                    contentType, USERNAME,
                    OBJECT_MAPPER.writeValueAsString(buildMetadataMap(input, testId)),
                    input.getByteArrayInputStream(),
                    OffsetDateTime.now()));
        }
        return results;
    }

    public Map<String, String> buildMetadataMap(Input input, String testId) {
        String testIdPrefix = testId.substring(0, 8);
        Map<String, String> metadataMap = new HashMap<>();

        for (Map.Entry<String, String> entry : input.getMetadataMap().entrySet()) {
            if (entry.getValue().contains(TEST_PREFIX_MACRO)) {
                metadataMap.put(entry.getKey(),
                        entry.getValue().replaceAll(TEST_PREFIX_MACRO, testIdPrefix));
            } else {
                metadataMap.put(entry.getKey(), entry.getValue());
            }
        }
        metadataMap.put(TEST_ID_KEY, testId);
        return metadataMap;
    }

    public void evaluate(String testId, IngressResult ingressed, Configuration config) {
        if (executor == null) {
            executor = Executors.newCachedThreadPool();
        }
        executor.submit(() -> {
            TestEvaluator testEvaluator = new TestEvaluator(deltaFilesService, contentStorageService, testResultRepo);
            try {
                testEvaluator.waitForDeltaFile(testId, ingressed, config.getExpectedDeltaFile(), config.getTimeout());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Unexpected exception caught from TestEvaluator: ", e);
            }
        });
    }

    public TestResult get(String id) {
        Optional<TestResult> result = testResultRepo.findById(id);
        return result.orElse(null);
    }

    public List<TestResult> getAll() {
        return testResultRepo.findAll();
    }

    public boolean remove(String id) {
        if (get(id) != null) {
            testResultRepo.deleteById(id);
            return true;
        }
        return false;
    }
}
