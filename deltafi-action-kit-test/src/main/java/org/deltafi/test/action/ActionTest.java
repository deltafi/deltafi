/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.actionkit.action.ContentResult;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.common.content.ActionContentStorageService;
import org.deltafi.common.test.content.InMemoryContentStorageService;
import org.deltafi.common.types.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Base class for action tests using the deprecated test framework.
 * @deprecated Use the DeltaFiTestRunner to set up the test and run the action directly.
 * The result can be verified using {@link org.deltafi.test.asserters.ActionResultAssertions}.
 */
@Deprecated
@ExtendWith(MockitoExtension.class)
@Slf4j
public abstract class ActionTest {
    /** Random DeltaFile ID used for test context. */
    protected final UUID DID = UUID.randomUUID();
    /** Hostname value used for test context. */
    protected final String HOSTNAME = "hostname";

    /** Mocked action properties. */
    @Mock
    protected ActionsProperties actionsProperties;

    /** In-memory content storage service for test isolation. */
    protected final ActionContentStorageService contentStorageService = new InMemoryContentStorageService();

    /**
     * Convert a UTC date string to the local timezone.
     * @param utcDateString the UTC date string to convert
     * @return the date string in the local timezone
     */
    protected String convertUTCDateToLocal(String utcDateString) {
        final DateTimeFormatter javaStdDateToStringFormatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
        ZonedDateTime utcDate = ZonedDateTime.parse(utcDateString, javaStdDateToStringFormatter);
        return utcDate.toOffsetDateTime().atZoneSameInstant(ZoneId.systemDefault()).format(javaStdDateToStringFormatter);
    }

    /**
     * Load IOContent entries into the content storage service as ActionContent.
     * @param contents the IOContent entries to load
     * @param testCase the test case providing the test name for resource lookup
     * @param stripIfStartsWith prefix to strip from content names
     * @return list of ActionContent backed by the storage service
     */
    protected List<ActionContent> getContents(List<? extends IOContent> contents, TestCaseBase<?> testCase, String stripIfStartsWith) {
        return contents.stream().map(ioContent -> {
            try {
                byte[] bytes = ioContent.getContent() == null ? getTestResource(testCase.getTestName(), ioContent.getName()) : ioContent.getContent();
                String name = ioContent.getName().startsWith(stripIfStartsWith) ? ioContent.getName().substring(stripIfStartsWith.length()) : ioContent.getName();
                Content content = contentStorageService.save(DID, bytes, name, ioContent.getContentType());
                return new ActionContent(content, contentStorageService);
            }
            catch(Throwable t) {
                log.error("Error loading content {}", ioContent.getName(), t);
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

    /**
     * Build a DeltaFileMessage from the given metadata and content.
     * @param metadata the source metadata map
     * @param content the content list to include
     * @return a DeltaFileMessage
     */
    protected DeltaFileMessage deltaFileMessage(Map<String, String> metadata, List<ActionContent> content) {
        return DeltaFileMessage.builder()
                .metadata(metadata == null ? new HashMap<>() : metadata)
                .contentList(content.stream().map(ContentConverter::convert).toList())
                .build();
    }

    /**
     * Build a test ActionContext with default values and the in-memory content storage service.
     * @return a populated ActionContext
     */
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

    /**
     * Hook called before executing the action. Override to customize test setup.
     * @param ignored the DeltaFileMessage that will be passed to the action
     * @param ignored2 the test case being executed
     */
    protected void beforeExecuteAction(DeltaFileMessage ignored, TestCaseBase<?> ignored2) {
    }

    /**
     * Execute the action configured in the test case and return the result.
     * @param testCase the test case to execute
     * @return the result from the action execution
     */
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

    /**
     * Execute the action and verify the result is a FilterResult matching the expected regex.
     * @param testCase the test case to execute
     */
    public void executeFilter(TestCaseBase<?> testCase) {
        FilterResult result = execute(testCase, FilterResult.class);

        if(!testCase.getRegex().matcher(result.getFilteredCause()).find()) {
            Assertions.fail("FilterResult message does not match expected regex: expected(" + testCase.getRegex().pattern() + ") actual(" + result.getFilteredCause() + ")");
        }
    }

    /**
     * Execute the action and verify the result is an ErrorResult matching the expected regex.
     * @param testCase the test case to execute
     */
    public void executeError(TestCaseBase<?> testCase) {
        ErrorResult result = execute(testCase, ErrorResult.class);

        if(!testCase.getRegex().matcher(result.toEvent().getError().getCause()).find()) {
            Assertions.fail("ErrorResult does not match expected regex: expected(" + testCase.getRegex().pattern() + ") actual(" + result.toEvent().getError().getCause() + ")");
        }
    }

    /**
     * Execute the action and verify the result based on the expected result type in the test case.
     * @param testCase the test case to execute
     */
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

    /**
     * Execute the action and cast the result to the expected type, failing if the types don't match.
     * @param <TC> the test case type
     * @param <RT> the expected result type
     * @param testCase the test case to execute
     * @param expectedResultType the expected result class
     * @return the action result cast to the expected type
     */
    @SneakyThrows
    public <TC extends TestCaseBase<?>, RT extends Result<?>> RT execute(TC testCase, Class<RT> expectedResultType) {
        ResultType result = callAction(testCase);

        if(!expectedResultType.isInstance(result)) {
            Assertions.fail(result.getClass().getSimpleName() + " returned when not expected");
        }

        return expectedResultType.cast(result);
    }

    /**
     * Normalize an ActionEvent for comparison by setting stop time and clearing content segments.
     * @param actionEvent the event to normalize
     * @return the normalized event
     */
    protected ActionEvent normalizeEvent(ActionEvent actionEvent) {
        actionEvent.setStop(OffsetDateTime.MAX);
        if (actionEvent.getTransform() != null) {
            actionEvent.getTransform().forEach(child -> child.getContent().forEach(this::normalizeContent));
        }

        return actionEvent;
    }

    /**
     * Clear the segments from a Content object for comparison purposes.
     * @param content the content to normalize
     */
    protected void normalizeContent(Content content) {
        content.setSegments(List.of());
    }

    /**
     * Load expected output content from IOContent entries and set it on the expected result.
     * @param expectedResult the result to populate with expected content
     * @param testCase the test case providing the test name for resource lookup
     * @param outputs the expected output IOContent entries
     * @return the expected content as byte arrays
     */
    protected List<byte[]> getExpectedContentOutput(ContentResult<?> expectedResult, TestCaseBase<?> testCase, List<? extends IOContent> outputs) {
        final List<ActionContent> expectedContent = getContents(outputs, testCase, "output.");
        expectedResult.setContent(expectedContent);

        return expectedContent.stream().map(ActionContent::loadBytes).toList();
    }

    /**
     * Assert that two lists of byte arrays are equal in size and content.
     * @param expected the expected byte arrays
     * @param actual the actual byte arrays
     */
    protected void assertContentIsEqual(List<byte[]> expected, List<byte[]> actual) {
        Assertions.assertEquals(expected.size(), actual.size(), "Expected content list size does not match actual content list size");

        for (int i=0; i < expected.size(); i++) {
            Assertions.assertEquals(new String(expected.get(i)), new String(actual.get(i)));
        }

        for (int ii = 0; ii < expected.size(); ii++) {
            Assertions.assertEquals(0, Arrays.compare(expected.get(ii), actual.get(ii)), "Content at index " + ii + " does not match expected content");
        }
    }

    /**
     * Load a test resource as bytes, returning null if the resource does not exist.
     * @param testCaseName name of the test case subdirectory
     * @param file name of the file to load
     * @return the file contents as bytes, or null if not found
     */
    protected byte[] getTestResourceBytesOrNull(String testCaseName, String file) {
        try(InputStream inputStream = getClass().getClassLoader().getResourceAsStream(getClass().getSimpleName() + "/" + testCaseName + "/" + file)) {
            return inputStream==null ? null : inputStream.readAllBytes();
        }
        catch(Throwable t) {
            return null;
        }
    }
}
