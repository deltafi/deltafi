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
package org.deltafi.test.action;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.ContentResult;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.test.storage.s3.InMemoryObjectStorageService;
import org.deltafi.common.types.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @deprecated Use the DeltaFiTestRunner to set up the test and run the action directly.
 * The result can be verified using {@link org.deltafi.test.asserters.ActionResultAssertions}.
 */
@Deprecated
@ExtendWith(MockitoExtension.class)
@Slf4j
public abstract class ActionTest {
    protected final UUID DID = UUID.randomUUID();
    protected final String HOSTNAME = "hostname";

    @Mock
    protected ActionsProperties actionsProperties;

    protected final ContentStorageService contentStorageService = new ContentStorageService(new InMemoryObjectStorageService());

    protected String convertUTCDateToLocal(String utcDateString) {
        final DateTimeFormatter javaStdDateToStringFormatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
        ZonedDateTime utcDate = ZonedDateTime.parse(utcDateString, javaStdDateToStringFormatter);
        return utcDate.toOffsetDateTime().atZoneSameInstant(ZoneId.systemDefault()).format(javaStdDateToStringFormatter);
    }

    protected List<ActionContent> getContents(List<? extends IOContent> contents, TestCaseBase<?> testCase, String stripIfStartsWith) {
        return contents.stream().map(ioContent -> {
            try {
                byte[] bytes = ioContent.getContent() == null ? getTestResource(testCase.getTestName(), ioContent.getName()) : ioContent.getContent();
                String name = ioContent.getName().startsWith(stripIfStartsWith) ? ioContent.getName().substring(stripIfStartsWith.length()) : ioContent.getName();
                Content content = contentStorageService.save(DID, bytes, name, ioContent.getContentType());
                return new ActionContent(content, contentStorageService);
            }
            catch(Throwable t) {
                log.error("Error loading content " + ioContent.getName(), t);
                return null;
            }
        }).toList();
    }

    /**
     * Use a supplier here to make sure code is only executed if it is required.  That way complex initializations
     * can be done but not waste CPU cycles.
     *
     * @param testCaseName name of the test case
     * @param file name of file to be used as test data
     *
     * @return test data byte array
     */
    protected byte[] getTestResource(String testCaseName, String file) {
        String filename = getClass().getSimpleName() + "/" + (testCaseName == null ? "" : testCaseName + "/") + file;
        try (InputStream ret = getClass().getClassLoader().getResourceAsStream(filename)) {
            if (ret == null) {
                throw new IllegalArgumentException(filename + " not found");
            }
            return ret.readAllBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException(filename + " not found");
        }
    }

    protected DeltaFileMessage deltaFileMessage(Map<String, String> metadata, List<ActionContent> content) {
        return DeltaFileMessage.builder()
                .metadata(metadata == null ? new HashMap<>() : metadata)
                .contentList(content.stream().map(ContentConverter::convert).toList())
                .build();
    }

    protected ActionContext context() {
        return ActionContext.builder()
                .did(DID)
                .actionName("name")
                .deltaFileName("filename")
                .hostname(HOSTNAME)
                .systemName("systemName")
                .actionVersion("1.0")
                .startTime(OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                .contentStorageService(contentStorageService)
                .build();
    }

    protected void beforeExecuteAction(DeltaFileMessage deltaFileMessage, TestCaseBase<?> testCase) {
    }

    protected ResultType callAction(TestCaseBase<?> testCase) {
        List<ActionContent> inputs = getContents(testCase.getInputs(), testCase, "input.");
        DeltaFileMessage deltaFileMessage = deltaFileMessage(testCase.getSourceMetadata(), inputs);

        Mockito.lenient().when(actionsProperties.getHostname()).thenReturn(HOSTNAME);

        beforeExecuteAction(deltaFileMessage, testCase);
        return testCase.getAction().executeAction(ActionInput.builder()
                .deltaFileMessages(List.of(deltaFileMessage))
                .actionContext(context())
                .actionParams(testCase.getParameters())
                .build());
    }

    public void executeFilter(TestCaseBase<?> testCase) {
        FilterResult result = execute(testCase, FilterResult.class);

        if(!testCase.getRegex().matcher(result.getFilteredCause()).find()) {
            Assertions.fail("FilterResult message does not match expected regex: expected(" + testCase.getRegex().pattern() + ") actual(" + result.getFilteredCause() + ")");
        }
    }

    public void executeError(TestCaseBase<?> testCase) {
        ErrorResult result = execute(testCase, ErrorResult.class);

        if(!testCase.getRegex().matcher(result.toEvent().getError().getCause()).find()) {
            Assertions.fail("ErrorResult does not match expected regex: expected(" + testCase.getRegex().pattern() + ") actual(" + result.toEvent().getError().getCause() + ")");
        }
    }

    public void execute(TestCaseBase<?> testCase) {
        if(testCase.getExpectedResultType() == ErrorResult.class) {
            executeError(testCase);
        }
        else if(testCase.getExpectedResultType() == FilterResult.class) {
            executeFilter(testCase);
        }
        else {
            Assertions.fail("Did not expect result type of " + testCase.getExpectedResultType());
        }
    }

    @SneakyThrows
    public <TC extends TestCaseBase<?>, RT extends Result<?>> RT execute(TC testCase, Class<RT> expectedResultType) {
        ResultType result = callAction(testCase);

        if(!expectedResultType.isInstance(result)) {
            Assertions.fail(result.getClass().getSimpleName() + " returned when not expected");
        }

        return expectedResultType.cast(result);
    }

    protected ActionEvent normalizeEvent(ActionEvent actionEvent) {
        actionEvent.setStop(OffsetDateTime.MAX);
        if (actionEvent.getTransform() != null) {
            actionEvent.getTransform().forEach(child -> child.getContent().forEach(this::normalizeContent));
        }

        return actionEvent;
    }

    protected void normalizeContent(Content content) {
        content.setSegments(List.of());
    }

    protected List<byte[]> getExpectedContentOutput(ContentResult<?> expectedResult, TestCaseBase<?> testCase, List<? extends IOContent> outputs) {
        final List<ActionContent> expectedContent = getContents(outputs, testCase, "output.");
        expectedResult.setContent(expectedContent);

        return expectedContent.stream().map(ActionContent::loadBytes).toList();
    }

    protected void assertContentIsEqual(List<byte[]> expected, List<byte[]> actual) {
        Assertions.assertEquals(expected.size(), actual.size(), "Expected content list size does not match actual content list size");

        for (int i=0; i < expected.size(); i++) {
            Assertions.assertEquals(new String(expected.get(i)), new String(actual.get(i)));
        }

        for (int ii = 0; ii < expected.size(); ii++) {
            Assertions.assertEquals(0, Arrays.compare(expected.get(ii), actual.get(ii)), "Content at index " + ii + " does not match expected content");
        }
    }

    protected byte[] getTestResourceBytesOrNull(String testCaseName, String file) {
        try(InputStream inputStream = getClass().getClassLoader().getResourceAsStream(getClass().getSimpleName() + "/" + testCaseName + "/" + file)) {
            return inputStream==null ? null : inputStream.readAllBytes();
        }
        catch(Throwable t) {
            return null;
        }
    }
}
