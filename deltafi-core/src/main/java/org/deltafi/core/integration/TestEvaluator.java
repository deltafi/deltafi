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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.*;
import org.deltafi.core.integration.config.ContentList;
import org.deltafi.core.integration.config.ExpectedActions;
import org.deltafi.core.integration.config.ExpectedDeltaFile;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.types.Action;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.types.DeltaFileFlow;
import org.deltafi.core.types.IngressResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestEvaluator {
    final int SLEEP_INTERVAL_MILLIS = 500; // 1 sec
    final int DEFAULT_TIMEOUT_MINUTES = 2; // 1 sec

    private final DeltaFilesService deltaFilesService;
    private final ContentStorageService contentStorageService;
    private final TestResultRepo testResultRepo;

    private final List<String> errors = new ArrayList<>();
    private boolean fatalError = false;

    public void waitForDeltaFile(String testId, IngressResult ingressed, ExpectedDeltaFile expected, Duration timeout) throws InterruptedException {
        OffsetDateTime startTime = OffsetDateTime.now();
        Duration maxTime;
        if (timeout == null || timeout.isZero()) {
            maxTime = Duration.ofMinutes(DEFAULT_TIMEOUT_MINUTES);
        } else {
            maxTime = timeout;
        }
        Duration timeRunning = Duration.of(0, ChronoUnit.SECONDS);

        boolean done = false;
        while (!fatalError && !done) {
            errors.clear();
            evaluate(ingressed, expected);
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
                    .status(TestStatus.SUCCESSFUL)
                    .start(startTime)
                    .stop(OffsetDateTime.now())
                    .build();
        } else {

            testResult = TestResult.builder()
                    .id(testId)
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

    public void evaluate(IngressResult ingressed, ExpectedDeltaFile expected) {
        DeltaFile deltaFile = deltaFilesService.getCachedDeltaFile(ingressed.did());
        if (deltaFile == null) {
            fatalError("Unable to retrieve DeltaFile, did: " + ingressed.did());
        } else {
            if (deltaFile.getStage() == expected.getStage()) {
                childCheck(deltaFile, expected);
            } else {
                maybeFatalError(deltaFile.isTerminal(),
                        "DeltaFile had expected stage " + expected.getStage() +
                                ", but was " + deltaFile.getStage());
            }
        }
    }

    private void childCheck(DeltaFile deltaFile, ExpectedDeltaFile expectedDeltaFile) {
        if (deltaFile.getChildDids().size() != expectedDeltaFile.getChildCount()) {
            maybeFatalError(
                    deltaFile.getChildDids().size() > expectedDeltaFile.getChildCount(),
                    "Expected " + expectedDeltaFile.getChildCount() +
                            " child DeltaFiles,  but had: " + deltaFile.getChildDids().size()
                            + ", " + deltaFile.getDid());
        } else if (!expectedDeltaFile.getChildren().isEmpty()) {
            compareAllChildren(deltaFile, expectedDeltaFile.getChildren());
        }
    }

    private void compareAllChildren(DeltaFile parentDeltaFile, List<ExpectedDeltaFile> children) {
        for (int index = 0; index < children.size(); index++) {
            UUID childDid = parentDeltaFile.getChildDids().get(index);
            DeltaFile childDeltaFile = deltaFilesService.getCachedDeltaFile(childDid);
            if (childDeltaFile == null || !childDeltaFile.isTerminal()) {
                errors.add("Cannot find child, or child not terminal yet. " + childDid);
            } else {
                testTerminalChild(index, childDeltaFile, children.get(index));
            }
        }
    }

    private void testTerminalChild(int index, DeltaFile deltaFile, ExpectedDeltaFile expected) {
        if (deltaFile.getStage() != expected.getStage()) {
            fatalError("Child " + index + ": expected " + expected.getStage() +
                    ", but was " + deltaFile.getStage() + ", " + deltaFile.getDid());
        } else {
            if (deltaFile.getChildDids().size() != expected.getChildCount()) {
                maybeFatalError(
                        deltaFile.getChildDids().size() > expected.getChildCount(),
                        "Child " + index + ": expected " +
                                expected.getChildCount() +
                                " children, but was " + deltaFile.getChildDids().size()
                                + ", " + deltaFile.getDid());

            } else {
                int flowActionMatchCount = 0;
                for (ExpectedActions ea : expected.getExpectedActions()) {
                    List<DeltaFileFlow> deltaFileFlows = deltaFile.getFlows()
                            .stream()
                            .filter(f -> flowMatch(f, ea.getFlow(), ea.getType()))
                            .toList();
                    if (deltaFileFlows.size() != 1) {
                        // TODO: Maybe handle >1 later?
                        fatalError("Found " + deltaFileFlows.size() + " flows matching "
                                + ea.getFlow() + "/" + ea.getType());
                    } else {
                        List<String> actionNames = deltaFileFlows.getFirst().getActions()
                                .stream().map(Action::getName)
                                .toList();

                        if (!actionNames.containsAll(ea.getActions())) {
                            fatalError("Child " + index + ": expected actions " +
                                    String.join(",", ea.getActions()) +
                                    " - but were " + String.join(",", actionNames)
                                    + ", " + deltaFile.getDid());

                        } else {
                            flowActionMatchCount++;
                        }
                    }
                }

                if (flowActionMatchCount != expected.getExpectedActions().size()) {
                    errors.add("Expected to match " + expected.getExpectedActions().size()
                            + " action sets, but matched only " + flowActionMatchCount);
                } else {

                    if (expected.getParentCount() != null && expected.getParentCount() != deltaFile.getParentDids().size()) {
                        errors.add("Child " + index + ": expected " +
                                expected.getParentCount() +
                                " parents, but was " + deltaFile.getParentDids().size()
                                + ", " + deltaFile.getDid());

                    } else {
                        if (contentMatches(deltaFile, expected.getExpectedContent())) {
                            childCheck(deltaFile, expected);
                        } else {
                            errors.add("Child " + index + ": content does not match");
                        }
                    }
                }
            }
        }
    }

    private boolean flowMatch(DeltaFileFlow flow, String name, FlowType type) {
        return flow.getName().equals(name) && flow.getType().equals(type);
    }

    private boolean contentMatches(DeltaFile deltaFile, ContentList expectedContent) {
        if (expectedContent == null) {
            return true;
        }

        List<DeltaFileFlow> deltaFileFlows = deltaFile.getFlows()
                .stream()
                .filter(f -> flowMatch(f, expectedContent.getFlow(), expectedContent.getType()))
                .toList();
        if (deltaFileFlows.size() != 1) {
            // TODO: Maybe handle >1 later?
            fatalError("Found " + deltaFileFlows.size() + " flows matching "
                    + expectedContent.getFlow() + "/" + expectedContent.getType());
            return false;
        }

        boolean found = false;
        for (Action action : deltaFileFlows.getFirst().getActions()) {
            if (action.getName().equals(expectedContent.getAction())
                // && action.getFlow().equals(expectedContent.getFlow())
            ) {

                List<Content> actualContent = action.getContent();
                if (actualContent.size() != expectedContent.getData().size()) {
                    fatalError("Content count differs");
                    return false;
                }

                // TODO: This needs work to verify 2+ contents
                for (int i = 0; i < actualContent.size(); i++) {
                    if (!actualContent.get(i).getName().equals(
                            expectedContent.getData().getFirst().getName())) {
                        fatalError("Expected content name " + expectedContent.getData().getFirst().getName()
                                + ", but was: " + actualContent.get(i).getName());
                        return false;
                    } else {
                        try {
                            String loadedContent = loadContent(actualContent.get(i));
                            if (loadedContent.equals(expectedContent.getData().get(i).getValue())) {
                                found = true;
                            } else {
                                fatalError("Content does mot match for content named "
                                        + actualContent.get(i).getName());
                                fatalError("Expected <" + expectedContent.getData().get(i).getValue()
                                        + ">, but was <" + loadedContent + ">");
                            }
                        } catch (Exception e) {
                            fatalError("Failed to retrieve content for " + actualContent.get(i).getName());
                        }
                    }
                }

                break;
            }

        }
        return found;
    }

    private String loadContent(Content content) throws ObjectStorageException, IOException {
        byte[] bytes = contentStorageService.load(content).readAllBytes();
        return new String(bytes);
    }
}
