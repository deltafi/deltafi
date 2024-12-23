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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.TestStatus;
import org.deltafi.common.types.integration.ExpectedContentData;
import org.deltafi.common.types.integration.ExpectedContentList;
import org.deltafi.common.types.integration.ExpectedDeltaFile;
import org.deltafi.common.types.integration.ExpectedFlow;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.types.Action;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.types.DeltaFileFlow;
import org.deltafi.core.types.integration.TestResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestEvaluator {
    final int SLEEP_INTERVAL_MILLIS = 500; // 1 sec
    final int DEFAULT_TIMEOUT_MINUTES = 2; // 1 sec
    final int MAX_DEPTH = 6;

    private final DeltaFilesService deltaFilesService;
    private final ContentStorageService contentStorageService;
    private final TestResultRepo testResultRepo;

    private final List<String> errors = new ArrayList<>();
    private boolean fatalError = false;

    public void waitForDeltaFile(String testId, String name, List<UUID> ingressDids, List<ExpectedDeltaFile> expectedDeltaFiles, Duration timeout) throws InterruptedException {
        OffsetDateTime startTime = OffsetDateTime.now();
        Duration maxTime;
        if (timeout == null || timeout.isZero()) {
            maxTime = Duration.ofMinutes(DEFAULT_TIMEOUT_MINUTES);
        } else {
            maxTime = timeout;
        }
        Duration timeRunning = Duration.of(0, ChronoUnit.SECONDS);

        if (ingressDids.size() != expectedDeltaFiles.size()) {
            fatalSizeError("ingressed DeltaFiles", expectedDeltaFiles.size(), ingressDids.size());
        }

        boolean done = false;
        while (!fatalError && !done) {
            errors.clear();
            for (int i = 0; i < expectedDeltaFiles.size(); i++) {
                evaluate(ingressDids.get(i), expectedDeltaFiles.get(i));
            }
            if (errors.isEmpty() || timeRunning.compareTo(maxTime) >= 0) {
                done = true;
            } else {
                Thread.sleep(SLEEP_INTERVAL_MILLIS);
                timeRunning = timeRunning.plusMillis(SLEEP_INTERVAL_MILLIS);
            }
        }

        TestResult testResult;
        if (errors.isEmpty()) {

            testResult = TestResult.builder()
                    .id(testId)
                    .testName(name)
                    .status(TestStatus.SUCCESSFUL)
                    .start(startTime)
                    .stop(OffsetDateTime.now())
                    .build();
        } else {

            testResult = TestResult.builder()
                    .id(testId)
                    .testName(name)
                    .status(TestStatus.FAILED)
                    .start(startTime)
                    .stop(OffsetDateTime.now())
                    .errors(errors)
                    .build();
        }
        testResultRepo.save(testResult);
    }

    private void maybeFatalError(boolean fatal, String msg) {
        errors.add(msg);
        if (fatal) {
            fatalError = true;
        }
    }

    private void fatalError(String msg) {
        fatalError = true;
        errors.add(msg);
    }

    private void fatalError(String field, String expected, String actual) {
        fatalError("Expected " + field + " to be '" + expected + "', but was '" + actual + "'");
    }

    private void fatalSizeError(String field, int expected, int actual) {
        fatalError("Expected " + field + " size to be '" + expected + "', but was '" + actual + "'");
    }

    private void fatalError(String label, String field, String expected, String actual) {
        fatalError("[" + label + "] Expected " + field + " to be '" + expected + "', but was '" + actual + "'");
    }

    private void fatalSizeError(String label, String field, int expected, int actual) {
        fatalError("[" + label + "] Expected " + field + " size to be '" + expected + "', but was '" + actual + "'");
    }

    public void evaluate(UUID did, ExpectedDeltaFile expected) {
        DeltaFile deltaFile = deltaFilesService.getCachedDeltaFile(did);
        if (deltaFile == null) {
            fatalError("Unable to retrieve DeltaFile, did: " + did.toString());
        } else {
            if (deltaFile.getStage() == expected.getStage()) {
                deltaFileCompare("Top", 1, deltaFile, expected);
            } else {
                maybeFatalError(deltaFile.isTerminal(),
                        "DeltaFile had expected stage " + expected.getStage() +
                                ", but was " + deltaFile.getStage());
            }

        }
    }

    private void childCheck(int depth, DeltaFile deltaFile, ExpectedDeltaFile expectedDeltaFile) {
        if (depth <= MAX_DEPTH) {
            if (deltaFile.getChildDids().size() != expectedDeltaFile.getChildCount()) {
                maybeFatalError(
                        deltaFile.getChildDids().size() > expectedDeltaFile.getChildCount(),
                        "Expected " + expectedDeltaFile.getChildCount() +
                                " child DeltaFiles,  but had: " + deltaFile.getChildDids().size()
                                + ", " + deltaFile.getDid());
            } else if (!expectedDeltaFile.getChildren().isEmpty()) {
                compareAllChildren(depth, deltaFile, expectedDeltaFile.getChildren());
            }
        } else {
            fatalError("Reached maximum depth comparison level: " + MAX_DEPTH);
        }
    }

    private void compareAllChildren(int depth, DeltaFile parentDeltaFile, List<ExpectedDeltaFile> children) {
        for (int index = 0; index < children.size(); index++) {
            UUID childDid = parentDeltaFile.getChildDids().get(index);
            DeltaFile childDeltaFile = deltaFilesService.getCachedDeltaFile(childDid);
            if (childDeltaFile == null || !childDeltaFile.isTerminal()) {
                errors.add("Cannot find child, or child not terminal yet. " + childDid);
            } else {
                deltaFileCompare("Child " + index, depth, childDeltaFile, children.get(index));
            }
        }
    }

    private void deltaFileCompare(String label, int depth, DeltaFile deltaFile, ExpectedDeltaFile expected) {
        if (deltaFile.getStage() != expected.getStage()) {
            fatalError(label + ":" + deltaFile.getDid(), "stage", expected.getStage().name(), deltaFile.getStage().name());
            return;
        }

        if (deltaFile.getChildDids().size() != expected.getChildCount()) {
            // If there are not enough children yet, give core/actions more time
            // and see if they get created. Else: Too many is fatal.
            maybeFatalError(
                    deltaFile.getChildDids().size() > expected.getChildCount(),
                    label + ": expected " +
                            expected.getChildCount() +
                            " children, but was " + deltaFile.getChildDids().size()
                            + ", " + deltaFile.getDid());
            return;
        }
        int flowActionMatchCount = expectedFlowsMatch(label, expected, deltaFile);
        if (flowActionMatchCount != expected.getExpectedFlows().size()) {
            errors.add("Expected to match " + expected.getExpectedFlows().size()
                    + " flow/action sets, but matched only " + flowActionMatchCount);
        } else {
            if (expected.getParentCount() != null && expected.getParentCount() != deltaFile.getParentDids().size()) {
                errors.add(label + ": expected " +
                        expected.getParentCount() +
                        " parents, but was " + deltaFile.getParentDids().size()
                        + ", " + deltaFile.getDid());

            } else {
                if (!contentMatches(deltaFile, expected.getExpectedContent())) {
                    errors.add(label + ": content does not match");
                } else if (!annotationsMatch(deltaFile, expected)) {
                    errors.add(label + ": annotations do not match");
                } else {
                    childCheck(depth + 1, deltaFile, expected);
                }
            }
        }
    }

    private int expectedFlowsMatch(String label, ExpectedDeltaFile expected, DeltaFile deltaFile) {
        int flowActionMatchCount = 0;
        for (ExpectedFlow ef : expected.getExpectedFlows()) {
            List<DeltaFileFlow> deltaFileFlows = deltaFile.getFlows()
                    .stream()
                    .filter(f -> flowMatch(f, ef.getFlow(), ef.getType()))
                    .toList();
            if (deltaFileFlows.size() != 1) {
                // TODO: Maybe handle >1 later?
                fatalSizeError(ef.getFlow(), "deltaFileFlows", 1, deltaFileFlows.size());
            } else {
                DeltaFileFlow deltaFileFlow = deltaFileFlows.getFirst();
                if (ef.getState() != deltaFileFlow.getState()) {
                    fatalError(ef.getFlow(), "state", ef.getState().name(), deltaFileFlow.getState().name());
                } else {
                    List<String> actionNames = deltaFileFlow.getActions()
                            .stream().map(Action::getName)
                            .toList();

                    if (!new HashSet<>(actionNames).containsAll(ef.getActions())) {
                        fatalError(label + ": expected actions " +
                                String.join(",", ef.getActions()) +
                                " - but were " + String.join(",", actionNames)
                                + ", " + deltaFile.getDid());
                    } else {
                        if (metadataMatches(ef, deltaFileFlow)) {
                            flowActionMatchCount++;
                        } else {
                            errors.add(label + ": metadata does not match for flow " + ef.getFlow());
                        }
                    }
                }
            }
        }
        return flowActionMatchCount;
    }

    private boolean metadataMatches(ExpectedFlow expectedFlow, DeltaFileFlow deltaFileFlow) {
        if (expectedFlow.getMetadata() != null) {
            if (Boolean.TRUE.equals(expectedFlow.getMetaExactMatch())) {
                if (expectedFlow.metadataToMap().equals(deltaFileFlow.getMetadata())) {
                    return true;
                } else {
                    errors.add("Expected metadata: " + expectedFlow.metadataToMap().toString()
                            + ", but was: " + deltaFileFlow.getMetadata().toString());
                    return false;
                }
            } else {
                // evaluate as "contains"
                for (KeyValue keyValue : expectedFlow.getMetadata()) {
                    if (!deltaFileFlow.getMetadata().containsKey(keyValue.getKey())) {
                        errors.add("Missing metadata key: " + keyValue.getKey());
                        return false;
                    } else if (!deltaFileFlow.getMetadata().get(keyValue.getKey()).equals(keyValue.getValue())) {
                        fatalError("Wrong value for metadata key: " + keyValue.getKey());
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean annotationsMatch(DeltaFile deltaFile, ExpectedDeltaFile expected) {
        if (expected.getAnnotations() == null) {
            return true;
        } else {
            return deltaFile.annotationMap().equals(expected.annotationsToMap());
        }
    }

    private boolean flowMatch(DeltaFileFlow flow, String name, FlowType type) {
        return flow.getName().equals(name) && flow.getType().equals(type);
    }

    private boolean contentMatches(DeltaFile deltaFile, ExpectedContentList expectedContent) {
        if (expectedContent == null) {
            // Test configuration does not want to verify output/content
            return true;
        }

        List<DeltaFileFlow> deltaFileFlows = deltaFile.getFlows()
                .stream()
                .filter(f -> flowMatch(f, expectedContent.getFlow(), expectedContent.getType()))
                .toList();
        if (deltaFileFlows.size() != 1) {
            // TODO: Maybe handle >1 later?
            fatalSizeError(expectedContent.getFlow() + "/" + expectedContent.getType(), "flows", 1, deltaFileFlows.size());
            return false;
        }

        for (Action action : deltaFileFlows.getFirst().getActions()) {
            // Find the action in this dataSource we want to compare content for
            if (action.getName().equals(expectedContent.getAction())) {
                return allContentsForActionAreEqual(action.getContent(), expectedContent.getData());
            }
        }
        // Did not find the action we wanted to compare
        return false;
    }

    private boolean allContentsForActionAreEqual(List<Content> actualContent, List<ExpectedContentData> expectedData) {
        if (actualContent.size() != expectedData.size()) {
            fatalSizeError("content", expectedData.size(), actualContent.size());
            return false;
        }

        for (int i = 0; i < actualContent.size(); i++) {
            if (!actualContent.get(i).getName().equals(expectedData.get(i).getName())) {
                fatalError("content name", expectedData.get(i).getName(), actualContent.get(i).getName());
                return false;
            }

            String expectedMediaType = expectedData.get(i).getMediaType();
            if (StringUtils.isNotEmpty(expectedMediaType) &&
                    !expectedMediaType.equals(actualContent.get(i).getMediaType())) {
                fatalError("mediaType", expectedMediaType, actualContent.get(i).getMediaType());
                return false;
            }

            if (!contentIsEqual(actualContent.get(i), expectedData.get(i))) {
                return false;
            }
        }

        return true;
    }

    private boolean contentIsEqual(Content actualContent, ExpectedContentData expectedData) {
        try {
            String loadedContent = loadContent(actualContent);
            String expectedValue = expectedData.getValue();

            // Compare either the expected 'value', or the 'contains' strings
            if (StringUtils.isNotEmpty(expectedValue)) {
                if (loadedContent.equals(expectedValue)) {
                    return true;
                } else {
                    fatalError(actualContent.getName(), "content", expectedValue, loadedContent);
                }
            } else {
                List<String> missing = expectedData.getContains().stream()
                        .filter(stringToContain -> !loadedContent.contains(stringToContain))
                        .toList();
                if (missing.isEmpty()) {
                    return true;
                } else {
                    fatalError("Did not find expected content: " + String.join(",", missing));
                }
            }
        } catch (Exception e) {
            fatalError("Failed to retrieve content for " + actualContent.getName());
        }
        return false;
    }

    private String loadContent(Content content) throws ObjectStorageException, IOException {
        byte[] bytes = contentStorageService.load(content).readAllBytes();
        return new String(bytes);
    }
}
