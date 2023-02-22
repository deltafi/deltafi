/**
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
package org.deltafi.common.splitter;

import org.assertj.core.api.Assertions;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.Segment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class ContentReferenceSplitterTest {
    private static final String WITH_COMMENTS_AND_HEADERS = """
                #ABC
                head
                1234
                5678
                """;

    private static final String WITH_HEADERS = """
                head
                1234
                5678
                """;

    private static final String DATA_ONLY = """
                1234
                5678
                9101
                """;

    private static final String MIDDLE_ROW_PLUS_HEADERS = """
                head
                1234
                56789
                1011
                """;

    private static final String LAST_ROW_PLUS_HEADERS = """
                head
                1234
                5678
                91011
                """;

    ContentReferenceSplitter splitter = new ContentReferenceSplitter(null);

    /**
     * Test segment handling with data that should all process successfully.
     * After the happy path test, lower the maxSize and verify the data can no longer
     * be segmented successfully.
     */
    @ParameterizedTest(name = "{index}: {4}")
    @MethodSource
    void segmentBoundaryTests(String content, List<String> expected, SplitterParams params, int boundary, String ignoredTestName) {
        params.setMaxSize(boundary);
        byte[] inputBytes = content.getBytes();

        // segment fits exactly, no attempt to add another line
        List<ContentReference> results = executeSplitter(content, params);
        Assertions.assertThat(results).hasSize(expected.size());
        IntStream.range(0, results.size()).forEach(i -> validate(readContentReference(results.get(i), inputBytes), expected.get(i)));

        // segment fits with extra room to exercise trying to fit another line that won't fit
        params.setMaxSize(boundary + 1);
        List<ContentReference> results2 = executeSplitter(content, params);
        Assertions.assertThat(results).hasSize(expected.size());
        IntStream.range(0, results.size()).forEach(i -> validate(readContentReference(results2.get(i), inputBytes), expected.get(i)));


        // segment will not fit
        params.setMaxSize(boundary - 1);
        Assertions.assertThatThrownBy(() -> executeSplitter(content, params)).isInstanceOf(SplitException.class);
    }

    static Stream<Arguments> segmentBoundaryTests() {
        List<String> withCommentsAndHeaders = List.of("#ABC\nhead\n1234\n", "head\n5678\n");
        List<String> expectedWithHeaders = List.of("head\n1234\n", "head\n5678\n");
        List<String> expectedDataOnly = List.of("1234\n", "5678\n", "9101\n");
        List<String> expectedMiddleRow = List.of("head\n1234\n", "head\n56789\n", "head\n1011\n");
        List<String> expectedLastRow =  List.of("head\n1234\n", "head\n5678\n", "head\n91011\n");

        // Middle row + header too big
        // Last row + header too big
        return Stream.of(
                Arguments.of(WITH_COMMENTS_AND_HEADERS, withCommentsAndHeaders, SplitterParams.builder().includeHeaders(true).commentChars("#").build(), 15, "first_segment_w_comments"),
                Arguments.of(WITH_HEADERS, expectedWithHeaders, SplitterParams.builder().includeHeaders(true).build(), 10, "first_segment_w_headers"),
                Arguments.of(DATA_ONLY, expectedDataOnly, SplitterParams.builder().includeHeaders(false).build(), 5, "first_segment_data_only"),
                Arguments.of(MIDDLE_ROW_PLUS_HEADERS, expectedMiddleRow, SplitterParams.builder().includeHeaders(true).build(), 11, "middle_segment_w_headers"),
                Arguments.of(LAST_ROW_PLUS_HEADERS, expectedLastRow, SplitterParams.builder().includeHeaders(true).build(), 11, "last_segment_w_headers")
        );
    }

    /**
     * Verify that data that cannot be properly segmented based on the SplitterParams
     * throw a SplitterException
     */
    @ParameterizedTest(name = "{index}: {3}")
    @MethodSource
    void testMaxSizeErrors(String content, String expected, SplitterParams params, String ignoredTestName) {
        Assertions.assertThatThrownBy(() -> executeSplitter(content, params))
                .isInstanceOf(SplitException.class)
                .hasMessage(expected);
    }

    static Stream<Arguments> testMaxSizeErrors() {
        return Stream.of(
                Arguments.of("#comment\nhead\n", CountingReader.LINE_OVERFLOW, SplitterParams.builder().includeHeaders(true).commentChars("#").maxSize(8).build(), "comment_too_large"),
                Arguments.of("#abc\nheader\nrow", CountingReader.LINE_OVERFLOW, SplitterParams.builder().includeHeaders(true).commentChars("#").maxSize(6).build(), "header_too_large"),
                Arguments.of("#abc\nhead\nrow", ContentReferenceSplitter.SEGMENT_OVERFLOW, SplitterParams.builder().includeHeaders(true).commentChars("#").maxSize(8).build(), "header+comment_too_large"),
                Arguments.of("#abc\nhead\nrow", ContentReferenceSplitter.SEGMENT_OVERFLOW, SplitterParams.builder().includeHeaders(true).commentChars("#").maxSize(12).build(), "header+comment+row_too_large"),
                Arguments.of("head\nrow", CountingReader.LINE_OVERFLOW, SplitterParams.builder().includeHeaders(true).commentChars("#").maxSize(6).build(), "header+row_too_large"),
                Arguments.of("#U,V,W\n#X,Y,Z\nA,B,C\n1,4,7\n2,5,8\n3,6,9", ContentReferenceSplitter.SEGMENT_OVERFLOW, SplitterParams.builder().includeHeaders(true).commentChars("#").maxSize(25).build(), "comments+row_too_large"),
                Arguments.of("head\ntoo big\nrow", CountingReader.LINE_OVERFLOW, SplitterParams.builder().includeHeaders(true).commentChars("#").maxSize(6).build(), "first_row_too_large"),
                Arguments.of("head\none\ntoo big\ntwo", CountingReader.LINE_OVERFLOW, SplitterParams.builder().includeHeaders(true).commentChars("#").maxSize(6).build(), "middle_row_too_large"),
                Arguments.of("head\none\ntwo\ntoo big", CountingReader.LINE_OVERFLOW, SplitterParams.builder().includeHeaders(true).commentChars("#").maxSize(6).build(), "last_row_too_large")
        );
    }

    @ParameterizedTest
    @MethodSource
    void testFindHeaders(String content, SplitterParams params, String expected) throws IOException {
        CountingReader countingReader = new CountingReader(new StringReader(content), params.getMaxSize());
        List<Segment> segment = splitter.findHeaderSegment(asContentReference(content), countingReader, params.getCommentChars());

        Assertions.assertThat(segment).hasSize(1);
        Assertions.assertThat(readSegment(segment.get(0), content.getBytes())).isEqualTo(expected);
    }

    private static Stream<Arguments> testFindHeaders() {
        SplitterParams.SplitterParamsBuilder builder = SplitterParams.builder().includeHeaders(true).maxSize(30);
        String header = "header";
        String header_cr = "header\r";
        String header_lf = "header\n";
        String header_crlf = "header\r\n";

        return Stream.of(
                Arguments.of("header\rrow", builder.commentChars("##").build(), header_cr),
                Arguments.of("header\nrow", builder.commentChars("##").build(), header_lf),
                Arguments.of("header\r\nrow", builder.commentChars("##").build(), header_crlf),
                Arguments.of("#ABC\r#ABC\rheader\rrow", builder.commentChars("#").build(), header_cr),
                Arguments.of("#ABC\n#ABC\nheader\nrow", builder.commentChars("#").build(), header_lf),
                Arguments.of("#ABC\r\n#ABC\rheader\r\nrow", builder.commentChars("#").build(), header_crlf),
                Arguments.of("//ABC\r//ABC\rheader\rrow\"", builder.commentChars("//").build(), header_cr),
                Arguments.of("//ABC\n//ABC\nheader\nrow\"", builder.commentChars("//").build(), header_lf),
                Arguments.of("//ABC\n//ABC\r\nheader\r\nrow\n", builder.commentChars("//").build(), header_crlf),
                Arguments.of("#comment\nheader", builder.commentChars("#").build(), header),
                Arguments.of("header", builder.commentChars("#").build(), header),
                Arguments.of("header\r", builder.commentChars("#").build(), header_cr),
                Arguments.of("header\n", builder.commentChars("#").build(), header_lf),
                Arguments.of("header\r\n", builder.commentChars("#").build(), header_crlf)
        );
    }

    @Test
    void testHeaderNotFound_allComments() {
        String content = "#row\n#comment";
        SplitterParams params = SplitterParams.builder().includeHeaders(true).commentChars("#").build();

        Assertions.assertThatThrownBy(() -> executeSplitter(content, params))
                    .isInstanceOf(SplitException.class)
                    .hasMessage("Unable to find the header line");
    }

    @Test
    void testHeaderNotFound_emptyInput() {
        String content = "";
        SplitterParams params = SplitterParams.builder().includeHeaders(true).commentChars("#").build();

        Assertions.assertThatThrownBy(() -> executeSplitter(content, params))
                .isInstanceOf(SplitException.class)
                .hasMessage("Unable to find the header line");
    }

    @Test
    void testEmptyInput_noHeaders() {
        String content = "";
        SplitterParams params = SplitterParams.builder().build();

        Assertions.assertThat(executeSplitter(content, params)).isEmpty();
    }

    @Test
    void testMaxRowsAndMaxSizeSet() {
        SplitterParams params = SplitterParams.builder().includeHeaders(false).maxSize(8).maxRows(3).build();

        String test = """
                1
                2
                3
                4
                567890
                a""";

        List<String> expected = new ArrayList<>();

        // first segment is capped by max rows, otherwise 4\n would fit in
        expected.add("1\n2\n3\n");
        // second segment is capped by max size, only a single row fits, the next row would make the segment too large
        expected.add("4\n");
        // last segment holds the remainder of the data including the last row which is added as left over bytes
        expected.add("567890\na");

        List<ContentReference> results = executeSplitter(test, params);

        byte[] inputBytes = test.getBytes();
        IntStream.range(0, results.size()).forEach(i -> printChildData(results.get(i), inputBytes, i));
        IntStream.range(0, results.size()).forEach(i -> validate(readContentReference(results.get(i), inputBytes), expected.get(i)));
    }

    @ParameterizedTest(name = "{index}: {3}")
    @MethodSource
    void testSplitInputStreamOutput(String content, List<String> expected, SplitterParams params, String ignoredTestName) {
        List<ContentReference> results = executeSplitter(content, params);

        byte[] inputBytes = content.getBytes();
        Assertions.assertThat(results).hasSize(expected.size());
        IntStream.range(0, results.size()).forEach(i -> printChildData(results.get(i), inputBytes, i));
        IntStream.range(0, results.size()).forEach(i -> validate(readContentReference(results.get(i), inputBytes), expected.get(i)));
    }

    private static Stream<Arguments> testSplitInputStreamOutput() {
        return Stream.of(
                Arguments.of(goodCsv(), expectedGoodNoHeaderCsv(), SplitterParams.builder().includeHeaders(false).maxSize(12).build(), "no_headers_max_size_12"),
                Arguments.of(goodCsv(), expectedGoodNoHeaderCsv(), SplitterParams.builder().includeHeaders(false).maxRows(2).build(), "no_headers_max_rows_2"),
                Arguments.of(goodCsv(), expectedGoodWithHeaderCsv(), SplitterParams.builder().includeHeaders(true).maxSize(18).build(), "headers_max_size_18"),
                Arguments.of(largeComments(), expectedLargeComments(), SplitterParams.builder().includeHeaders(true).commentChars("#").maxSize(34).build(), "headers_max_size_34"),
                Arguments.of(goodCsv(), expectedGoodWithHeaderCsv(), SplitterParams.builder().includeHeaders(true).maxRows(2).build(), "headers_max_rows_2"),
                Arguments.of(goodCsvWithComments(), expectedGoodWithHeaderAndCommentsCsv(), SplitterParams.builder().includeHeaders(true).commentChars("#").maxSize(32).build(), "headers_max_size_32_#comment"),
                Arguments.of(goodCsvWithComments(), expectedGoodWithHeaderAndCommentsCsv(), SplitterParams.builder().includeHeaders(true).commentChars("#").maxRows(2).build(), "headers_max_rows_2_#comment")
        );
    }

    void validate(String result, String expected) {
        Assertions.assertThat(result).isEqualTo(expected);
    }

    private List<ContentReference> executeSplitter(String content, SplitterParams params) {
        ContentReference contentReference = asContentReference(content);

        try (InputStream inputStream = asInputStream(content)) {
            return splitter.splitInputStream(contentReference, inputStream, params);
        } catch (IOException e) {
            Assertions.fail("failed to get input stream", e);
        }
        return null;
    }

    private ContentReference asContentReference(String content) {
        Segment segment = new Segment(UUID.randomUUID().toString(), 0, content.length(), "did");
        return new ContentReference("application/text", segment);
    }

    private InputStream asInputStream(String in) {
        return new ByteArrayInputStream(in.getBytes(StandardCharsets.UTF_8));
    }

    private void printChildData(ContentReference contentReference, byte[] value, int childNum) {
        System.out.println("Child #: " + childNum);
        contentReference.getSegments().forEach(segment -> System.out.print(readSegment(segment, value)));
    }

    private String readContentReference(ContentReference contentReference, byte[] value) {
        return contentReference.getSegments().stream().map(s -> readSegment(s, value)).collect(Collectors.joining());
    }

    private String readSegment(Segment segment, byte[] value) {
        return new String(value, (int) segment.getOffset(), (int) segment.getSize());
    }

    private static String goodCsv() {
        return """
                A,B,C
                1,4,7
                2,5,8
                3,6,9
                """;
    }

    private static String goodCsvWithComments() {
        return """
                #U,V,W
                #X,Y,Z
                A,B,C
                1,4,7
                2,5,8
                3,6,9
                """;
    }

    private static String largeComments() {
        return """
                #12345
                #67890
                header
                A,B,C
                D,E,F
                G,H,I
                J,K,L,M,N,O,P,Q
                R,S
                """;
    }

    private static List<String> expectedLargeComments() {
        List<String> expected = new ArrayList<>();
        expected.add("#12345\n#67890\nheader\nA,B,C\nD,E,F\n");
        expected.add("header\nG,H,I\nJ,K,L,M,N,O,P,Q\nR,S\n");
        return expected;
    }

    private static List<String> expectedGoodNoHeaderCsv() {
        List<String> expected = new ArrayList<>();
        expected.add("A,B,C\n1,4,7\n");
        expected.add("2,5,8\n3,6,9\n");
        return expected;
    }

    private static List<String> expectedGoodWithHeaderCsv() {
        List<String> expected = new ArrayList<>();
        expected.add("A,B,C\n1,4,7\n2,5,8\n");
        expected.add("A,B,C\n3,6,9\n");
        return expected;
    }

    private static List<String> expectedGoodWithHeaderAndCommentsCsv() {
        List<String> expected = new ArrayList<>();
        expected.add("#U,V,W\n#X,Y,Z\nA,B,C\n1,4,7\n2,5,8\n");
        expected.add("A,B,C\n3,6,9\n");
        return expected;
    }

}