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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@ExtendWith(MockitoExtension.class)
@Slf4j
public abstract class ActionTest {

    private static final Map<String, Pattern> NORMALIZED_REPLACEMENTS_MAP = Map.of(
            "\"stop\":\"stopDateTime\"", Pattern.compile("\"stop\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(-\\d{2}:\\d{2})?Z?\""),
            "\"segments\":[]", Pattern.compile("\"segments\":\\[(.*?)\\]")
    );

    protected final String DID = "did";
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
                byte[] bytes = getTestResourceOrDefault(testCase.getTestName(), ioContent.getName(),
                        () -> new ByteArrayInputStream(ioContent.getName().getBytes(StandardCharsets.UTF_8)))
                        .readAllBytes();
                String name = ioContent.getName().startsWith(stripIfStartsWith) ? ioContent.getName().substring(stripIfStartsWith.length()) : ioContent.getName();
                Content content = contentStorageService.save(DID, bytes, name, ioContent.getContentType());
                return new ActionContent(content, contentStorageService);
            }
            catch(Throwable t) {
                log.error("Error getting content", t);
                return null;
            }
        }).toList();
    }

    protected List<ActionContent> createContents(TestCaseBase<?> testCase, List<? extends IOContent> outputs) {
        return outputs.stream().map(convertOutputToContent(testCase))
                .toList();
    }

    protected Function<IOContent, ActionContent> convertOutputToContent(TestCaseBase<?> testCase) {
        return (output) -> {
            try {
                byte[] bytes = getTestResourceOrEmpty(testCase.getTestName(), output.getName()).readAllBytes();
                String name = output.getName().startsWith("output.") ? output.getName().substring(7) : output.getName();
                Content content = contentStorageService.save(DID, bytes, name, output.getContentType());
                return new ActionContent(content, contentStorageService);
            }
            catch (Throwable t) {
                log.error("Error converting output to content", t);
                return null;
            }
        };
    }

    /**
     * Use a supplier here to make sure code is only executed if it is required.  That way complex initializations
     * can be done but not waste CPU cycles.
     *
     * @param testCaseName name of the test case
     * @param file name of file to be used as test data
     * @param supplier supplier input stream to be used for test data
     *
     * @return test data input stream
     */
    protected InputStream getTestResourceOrDefault(String testCaseName, String file, Supplier<InputStream> supplier) {
        try(InputStream ret = getClass().getClassLoader().getResourceAsStream(getClass().getSimpleName() + "/" + testCaseName + "/" + file)) {
            return ret==null ? supplier.get() : new ByteArrayInputStream(ret.readAllBytes());
        }
        catch(Throwable t) {
            return supplier.get();
        }
    }

    protected DeltaFileMessage deltaFileMessage(Map<String, String> metadata, List<ActionContent> content) {
        return DeltaFileMessage.builder()
                .metadata(metadata == null ? new HashMap<>() : metadata)
                .contentList(content.stream().map(ContentConverter::convert).toList())
                .domains(new ArrayList<>())
                .enrichments(new ArrayList<>())
                .build();
    }

    protected ActionContext context() {
        return ActionContext.builder()
                .did(DID)
                .name("name")
                .sourceFilename("filename")
                .ingressFlow(null)
                .egressFlow(null)
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

    protected String normalizeData(String input) {
        return NORMALIZED_REPLACEMENTS_MAP.entrySet().stream().reduce(input, this::applyPattern, (u1, u2) -> u2);
    }

    private String applyPattern(String input, Map.Entry<String, Pattern> entry) {
        return entry.getValue().matcher(input).replaceAll(entry.getKey());
    }

    protected InputStream getTestResourceOrEmpty(String testCaseName, String file) {
        return getTestResourceOrDefault(testCaseName, file, () -> new ByteArrayInputStream(new byte[0]));
    }

    protected <R> List<R> orderListByAnother(List<R> orderBy, List<R> toOrder, Function<R, ?> getIdProperty) {
        return orderBy.stream().map(getIdProperty).map(
                itemValue -> getObjectByLambda(toOrder, item -> getIdProperty.apply(item).equals(itemValue), () ->
                Assertions.fail("Unable to find correct item in list to normalize"))).toList();
    }

    protected <R> R getObjectByLambda(List<R> list, Predicate<R> isCorrectOne, Supplier<R> ifNotFound) {
        return list.stream().filter(isCorrectOne).findFirst().orElseGet(ifNotFound);
    }

    protected List<byte[]> getExpectedContentOutputNormalized(ContentResult<?> expectedResult, ContentResult<?> actualResult, TestCaseBase<?> testCase, List<? extends IOContent> outputs) {
        final List<ActionContent> expectedContent = createContents(testCase, outputs);

        List<ActionContent> normalizedExpectedContent = orderListByAnother(actualResult.getContent(), expectedContent, ActionContent::getName);
        expectedResult.setContent(normalizedExpectedContent);

        return normalizedExpectedContent.stream().map(ActionContent::loadBytes).toList();
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
