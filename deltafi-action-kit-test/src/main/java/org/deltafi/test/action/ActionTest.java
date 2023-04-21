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
import org.deltafi.actionkit.action.DataAmendedResult;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.Segment;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
            "stop=stopDateTime", Pattern.compile("stop=\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(-\\d{2}:\\d{2})?"),
            "Segment(uuid=00000000-0000-0000-0000-000000000001", Pattern.compile("Segment\\(uuid=[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    );


    protected final String DID = "did";
    protected final String HOSTNAME = "hostname";

    @Mock
    protected ActionsProperties actionsProperties;

    @Mock
    protected ContentStorageService contentStorageService;

    protected String convertUTCDateToLocal(String utcDateString) {
        final DateTimeFormatter javaStdDateToStringFormatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
        ZonedDateTime utcDate = ZonedDateTime.parse(utcDateString, javaStdDateToStringFormatter);
        return utcDate.toOffsetDateTime().atZoneSameInstant(ZoneId.systemDefault()).format(javaStdDateToStringFormatter);
    }

    @SneakyThrows
    private void setMocksForLoad(TestCaseBase<?> testCase, ContentReference reference, byte[] content) {
        if(testCase.getExceptionLocation() == TestCaseBase.ExceptionLocation.STORAGE_READ) {
            Mockito.lenient().when(contentStorageService.load(Mockito.eq(reference))).thenThrow(testCase.getException());
        }
        else {
            Mockito.lenient().when(contentStorageService.load(Mockito.eq(reference))).thenAnswer((invocation) -> new ByteArrayInputStream(content));
            Mockito.lenient().when(reference.subreference(Mockito.anyLong(), Mockito.anyLong())).thenAnswer(createContentSubreference(content));
        }
    }

    protected List<Content> getContents(List<? extends IOContent> contents, TestCaseBase<?> testCase, String stripIfStartsWith) {
        return contents.stream().map(ioContent -> {
            // Grab the content, setup the mocks, then create a Content object that describes it
            try {
                byte[] content = getTestResourceOrDefault(testCase.getTestName(), ioContent.getName(),
                        () -> new ByteArrayInputStream(ioContent.getName().getBytes(StandardCharsets.UTF_8)))
                        .readAllBytes();
                final Segment segment = new Segment(UUID.randomUUID().toString(), ioContent.getOffset(), content.length, DID);
                ContentReference reference = Mockito.spy(new ContentReference(ioContent.getContentType(), segment));

                setMocksForLoad(testCase, reference, content);

                return Content.newBuilder()
                        .name(ioContent.getName().startsWith(stripIfStartsWith) ? ioContent.getName().substring(stripIfStartsWith.length()) : ioContent.getName())
                        .contentReference(reference)
                        .build();
            }
            catch(Throwable t) {
                t.printStackTrace();
                return null;
            }
        }).toList();
    }

    protected List<Content> createContents(TestCaseBase<?> testCase, List<? extends IOContent> outputs) {
        return outputs.stream().map(convertOutputToContent(testCase))
                .toList();
    }

    protected Function<IOContent, Content> convertOutputToContent(TestCaseBase<?> testCase) {
        return (output) -> {
            try {
                byte[] content = getTestResourceOrEmpty(testCase.getTestName(), output.getName()).readAllBytes();
                final Segment segment = new Segment(UUID.randomUUID().toString(), output.getOffset(), content.length, DID);
                ContentReference reference = Mockito.spy(new ContentReference(output.getContentType(), segment));
                setMocksForLoad(testCase, reference, content);
                return Content.newBuilder()
                        .name(output.getName().startsWith("output.") ? output.getName().substring(7) : output.getName())
                        .contentReference(reference)
                        .build();
            }
            catch(Throwable t) {
                t.printStackTrace();
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

    protected DeltaFile deltaFile(Map<String, String> metadata, List<Content> content) {
        return DeltaFile.newBuilder().sourceInfo(
                        SourceInfo.builder().metadata(
                                        metadata == null ? new HashMap<>() : metadata)
                                .filename("filename")
                                .flow("flow")
                                .build())
                .protocolStack(Collections.singletonList(ProtocolLayer.builder()
                        .metadata(new HashMap<>())
                        .action("action")
                        .content(content)
                        .build()))
                // "Input" could also be "FormattedData" from a FormatAction to support Egress actions
                .formattedData(content.stream()
                        .map(Content::getContentReference)
                        .map(cr -> FormattedData.newBuilder().contentReference(cr).build())
                        .toList())
                .domains(new ArrayList<>())
                .did(DID)
                .build();
    }

    protected ActionContext context() {
        return ActionContext.builder()
                .did(DID)
                .name("name")
                .ingressFlow(null)
                .egressFlow(null)
                .hostname(HOSTNAME)
                .systemName("systemName")
                .actionVersion("1.0")
                .startTime(OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                .contentStorageService(contentStorageService)
                .build();
    }

    protected void beforeExecuteAction(DeltaFile deltaFile, TestCaseBase<?> testCase) {
    }

    protected ResultType callAction(TestCaseBase<?> testCase) {
        List<Content> inputs = getContents(testCase.getInputs(), testCase, "input.");
        DeltaFile deltaFile = deltaFile(testCase.getSourceMetadata(), inputs);

        Mockito.lenient().when(actionsProperties.getHostname()).thenReturn(HOSTNAME);

        beforeExecuteAction(deltaFile, testCase);
        return testCase.getAction().executeAction(ActionInput.builder()
                .deltaFile(deltaFile)
                .actionContext(context())
                .actionParams(testCase.getParameters())
                .build());
    }

    protected List<Content> createContentReferencesForSaveMany(InvocationOnMock invocation) throws ObjectStorageException, IOException {
        String did = invocation.getArgument(0);
        Map<Content, byte[]> contentToBytes = invocation.getArgument(1);
        List<Content> contentList = new ArrayList<>();

        for(Map.Entry<Content, byte[]> contentToByte : contentToBytes.entrySet()) {
            final Content content = contentToByte.getKey();
            final byte[] bytes = contentToByte.getValue();

            content.setContentReference(createContentReference(did, MediaType.APPLICATION_OCTET_STREAM, bytes));
            contentList.add(content);
        }

        return contentList;
    }

    protected ContentReference createContentReference(InvocationOnMock invocation) throws IOException, ObjectStorageException {
        String did = invocation.getArgument(0);
        String contentType = invocation.getArgument(2);
        Object bytesOrStream = invocation.getArgument(1);

        return createContentReference(did, contentType, bytesOrStream);
    }

    protected ContentReference createContentReference(String did, String contentType, Object bytesOrStream) throws IOException, ObjectStorageException {
        final byte[] bytes;

        if(bytesOrStream instanceof InputStream) {
            bytes = ((InputStream) bytesOrStream).readAllBytes();
        }
        else {
            bytes = (byte[])bytesOrStream;
        }

        final Segment segment = new Segment(UUID.randomUUID().toString(), 0, bytes.length, did);
        ContentReference reference = Mockito.spy(new ContentReference(contentType, segment));
        Mockito.lenient().when(reference.subreference(Mockito.anyLong(), Mockito.anyLong())).thenAnswer(createContentSubreference(bytes));
        Mockito.lenient().when(contentStorageService.load(Mockito.eq(reference))).thenAnswer((invocation) -> new ByteArrayInputStream(bytes));

        return reference;
    }

    protected Answer<ContentReference> createContentSubreference(byte[] bytes) {
        return invocation -> {
            Long offset = invocation.getArgument(0);
            Long size = invocation.getArgument(1);
            ContentReference reference = Mockito.spy((ContentReference)invocation.callRealMethod());
            Mockito.lenient().when(reference.subreference(Mockito.anyLong(), Mockito.anyLong())).thenAnswer(createContentSubreference(bytes));
            Mockito.lenient().when(contentStorageService.load(Mockito.eq(reference))).thenAnswer((invocation2) -> new ByteArrayInputStream(bytes, offset.intValue(), size.intValue()));
            return reference;
        };
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
        // Setup some apis so that we can get data back out
        if(testCase.getExceptionLocation() == TestCaseBase.ExceptionLocation.STORAGE_WRITE) {
            Mockito.lenient().when(contentStorageService.save(Mockito.anyString(), Mockito.any(byte[].class), Mockito.anyString())).thenThrow(testCase.getException());
            Mockito.lenient().when(contentStorageService.save(Mockito.anyString(), Mockito.any(InputStream.class), Mockito.anyString())).thenThrow(testCase.getException());
            Mockito.lenient().when(contentStorageService.saveMany(Mockito.anyString(), Mockito.anyMap())).thenThrow(testCase.getException());
        }
        else {
            Mockito.lenient().when(contentStorageService.save(Mockito.anyString(), Mockito.any(byte[].class), Mockito.anyString())).thenAnswer(this::createContentReference);
            Mockito.lenient().when(contentStorageService.save(Mockito.anyString(), Mockito.any(InputStream.class), Mockito.anyString())).thenAnswer(this::createContentReference);
            Mockito.lenient().when(contentStorageService.saveMany(Mockito.anyString(), Mockito.anyMap())).thenAnswer(this::createContentReferencesForSaveMany);
        }

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

    protected List<byte[]> getExpectedContentOutputNormalized(DataAmendedResult expectedResult, DataAmendedResult actualResult, TestCaseBase<?> testCase, List<? extends IOContent> outputs) {
        final List<Content> expectedContent = createContents(testCase, outputs);

        List<Content> normalizedExpectedContent = orderListByAnother(actualResult.getContent(), expectedContent, Content::getName);
        expectedResult.setContent(normalizedExpectedContent);

        return normalizedExpectedContent.stream().map(this::getContent).toList();
    }

    protected byte[] getContent(Content content) {
        ContentReference ref = content.getContentReference();
        try (InputStream stream = contentStorageService.load(ref)) {
            assert(stream!=null);
            return stream.readAllBytes();
        }
        catch(Throwable t) {
            return null;
        }
    }

    protected void assertContentIsEqual(List<byte[]> expected, List<byte[]> actual) {
        Assertions.assertEquals(expected.size(), actual.size(), "Expected content list size does not match actual content list size");
        for(int ii=0; ii<expected.size(); ii++) {
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
