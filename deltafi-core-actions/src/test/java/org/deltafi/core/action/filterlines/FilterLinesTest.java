/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.filterlines;

// ABOUTME: Tests for the FilterLines transform action.
// ABOUTME: Verifies line filtering with head, tail, skip, include/exclude patterns, and various edge cases.

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.ErrorResultAssert;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterLinesTest {

    FilterLines action = new FilterLines();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    // --- Head Lines Tests ---

    @Test
    void headLinesKeepsFirstN() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(3);

        ResultType result = transform(params, """
                line1
                line2
                line3
                line4
                line5
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        line3
                        """);
    }

    @Test
    void headLinesZeroReturnsEmpty() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(0);

        ResultType result = transform(params, """
                line1
                line2
                line3
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "");
    }

    @Test
    void headLinesExceedsTotalRetainsAll() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(100);

        ResultType result = transform(params, """
                line1
                line2
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        """);
    }

    // --- Tail Lines Tests ---

    @Test
    void tailLinesKeepsLastN() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setTailLines(2);

        ResultType result = transform(params, """
                line1
                line2
                line3
                line4
                line5
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line4
                        line5
                        """);
    }

    @Test
    void tailLinesZeroReturnsEmpty() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setTailLines(0);

        ResultType result = transform(params, """
                line1
                line2
                line3
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "");
    }

    @Test
    void tailLinesExceedsTotalRetainsAll() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setTailLines(100);

        ResultType result = transform(params, """
                line1
                line2
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        """);
    }

    // --- Skip Lines Tests ---

    @Test
    void skipLinesSkipsFirstN() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setSkipLines(2);

        ResultType result = transform(params, """
                header1
                header2
                data1
                data2
                data3
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        data1
                        data2
                        data3
                        """);
    }

    @Test
    void skipLinesExceedsTotalReturnsEmpty() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setSkipLines(100);

        ResultType result = transform(params, """
                line1
                line2
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "");
    }

    // --- Include Patterns Tests ---

    @Test
    void includePatternsKeepsMatchingLines() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setIncludePatterns(List.of("ERROR"));

        ResultType result = transform(params, """
                INFO: started
                ERROR: failed
                INFO: retrying
                ERROR: timeout
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        ERROR: failed
                        ERROR: timeout
                        """);
    }

    @Test
    void includePatternsWithRegex() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setIncludePatterns(List.of("^\\d{4}-\\d{2}-\\d{2}"));

        ResultType result = transform(params, """
                2024-01-15 INFO: start
                malformed line
                2024-01-16 ERROR: fail
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        2024-01-15 INFO: start
                        2024-01-16 ERROR: fail
                        """);
    }

    @Test
    void includePatternsNoMatchReturnsEmpty() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setIncludePatterns(List.of("NOTFOUND"));

        ResultType result = transform(params, """
                line1
                line2
                line3
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "");
    }

    @Test
    void includePatternsMultiplePatternsOrLogic() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setIncludePatterns(List.of("ERROR", "WARN"));

        ResultType result = transform(params, """
                INFO: ok
                ERROR: fail
                WARN: warning
                DEBUG: trace
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        ERROR: fail
                        WARN: warning
                        """);
    }

    @Test
    void includePatternsMultipleRegexPatterns() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setIncludePatterns(List.of("^ERROR:", "^FATAL:", "exception", "failed"));

        ResultType result = transform(params, """
                INFO: starting
                ERROR: something broke
                FATAL: crash
                INFO: exception caught
                DEBUG: ok
                WARN: operation failed
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        ERROR: something broke
                        FATAL: crash
                        INFO: exception caught
                        WARN: operation failed
                        """);
    }

    @Test
    void includePatternsAndModeRequiresAllMatches() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setIncludePatterns(List.of("ERROR", "database"));
        params.setIncludeMatchMode(MatchMode.AND);

        ResultType result = transform(params, """
                ERROR: database connection failed
                ERROR: timeout
                INFO: database ok
                ERROR: database query failed
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        ERROR: database connection failed
                        ERROR: database query failed
                        """);
    }

    @Test
    void includePatternsAndModeNoMatchWhenNotAllPresent() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setIncludePatterns(List.of("foo", "bar", "baz"));
        params.setIncludeMatchMode(MatchMode.AND);

        ResultType result = transform(params, """
                foo bar
                foo baz
                bar baz
                foo bar baz
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        foo bar baz
                        """);
    }

    @Test
    void includePatternsOrModeMatchesAny() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setIncludePatterns(List.of("apple", "banana"));
        params.setIncludeMatchMode(MatchMode.OR);

        ResultType result = transform(params, """
                I like apple
                I like banana
                I like cherry
                I like apple and banana
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        I like apple
                        I like banana
                        I like apple and banana
                        """);
    }

    // --- Exclude Patterns Tests ---

    @Test
    void excludePatternsRemovesMatchingLines() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setExcludePatterns(List.of("^#"));

        ResultType result = transform(params, """
                # comment
                key=value
                # another comment
                foo=bar
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        key=value
                        foo=bar
                        """);
    }

    @Test
    void excludePatternsAllMatchReturnsEmpty() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setExcludePatterns(List.of(".*"));

        ResultType result = transform(params, """
                line1
                line2
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "");
    }

    @Test
    void excludePatternsMultiplePatternsOrLogic() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setExcludePatterns(List.of("^#", "^//", "^\\s*$"));

        ResultType result = transform(params, """
                # shell comment
                // C comment
                actual code
                  \s
                more code
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        actual code
                        more code
                        """);
    }

    @Test
    void excludePatternsRemoveMultiplePatterns() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setExcludePatterns(List.of("DEBUG", "TRACE", "VERBOSE"));

        ResultType result = transform(params, """
                INFO: start
                DEBUG: details
                ERROR: fail
                TRACE: more details
                VERBOSE: noise
                WARN: warning
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        INFO: start
                        ERROR: fail
                        WARN: warning
                        """);
    }

    @Test
    void excludePatternsAndModeRemovesOnlyWhenAllMatch() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setExcludePatterns(List.of("spam", "advertisement"));
        params.setExcludeMatchMode(MatchMode.AND);

        ResultType result = transform(params, """
                spam message
                advertisement here
                spam advertisement combo
                normal content
                """);

        // Only removes line containing BOTH "spam" AND "advertisement"
        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        spam message
                        advertisement here
                        normal content
                        """);
    }

    @Test
    void excludePatternsOrModeRemovesAnyMatch() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setExcludePatterns(List.of("spam", "advertisement"));
        params.setExcludeMatchMode(MatchMode.OR);

        ResultType result = transform(params, """
                spam message
                advertisement here
                spam advertisement combo
                normal content
                """);

        // Removes lines containing "spam" OR "advertisement"
        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        normal content
                        """);
    }

    // --- Keep Empty Tests ---

    @Test
    void keepEmptyFalseRemovesEmptyLines() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setKeepEmpty(false);

        ResultType result = transform(params, """
                line1

                line2


                line3
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        line3
                        """);
    }

    @Test
    void keepEmptyTrueRetainsEmptyLines() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setKeepEmpty(true);

        ResultType result = transform(params, """
                line1

                line2
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1

                        line2
                        """);
    }

    // --- Line Delimiter Tests ---

    @Test
    void customLineDelimiter() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setLineDelimiter("|");
        params.setHeadLines(2);

        ResultType result = transform(params, "line1|line2|line3|line4|");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "line1|line2|");
    }

    @Test
    void crlfDelimiter() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setLineDelimiter("\r\n");
        params.setHeadLines(2);

        ResultType result = transform(params, "line1\r\nline2\r\nline3\r\n");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "line1\r\nline2\r\n");
    }

    @Test
    void preserveDelimiterFalseUsesSystemSeparator() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setLineDelimiter("|");
        params.setPreserveDelimiter(false);
        params.setHeadLines(2);

        ResultType result = transform(params, "line1|line2|line3|");

        String expected = "line1" + System.lineSeparator() + "line2" + System.lineSeparator();
        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", expected);
    }

    // --- Combined Operations Tests ---

    @Test
    void skipThenHeadLines() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setSkipLines(1);
        params.setHeadLines(2);

        ResultType result = transform(params, """
                header
                data1
                data2
                data3
                data4
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        data1
                        data2
                        """);
    }

    @Test
    void headAndTailLinesUnion() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(2);
        params.setTailLines(2);

        ResultType result = transform(params, """
                line1
                line2
                line3
                line4
                line5
                """);

        // Union: first 2 (line1, line2) + last 2 (line4, line5)
        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        line4
                        line5
                        """);
    }

    @Test
    void headAndTailLinesWithOverlap() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(3);
        params.setTailLines(3);

        ResultType result = transform(params, """
                line1
                line2
                line3
                line4
                line5
                """);

        // Union with overlap: first 3 (1,2,3) + last 3 (3,4,5) = all 5, deduplicated
        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        line3
                        line4
                        line5
                        """);
    }

    @Test
    void headAndTailLinesCoverAll() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(10);
        params.setTailLines(10);

        ResultType result = transform(params, """
                line1
                line2
                line3
                """);

        // Both exceed size, so all lines are kept
        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        line3
                        """);
    }

    @Test
    void includeThenExcludePatterns() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setIncludePatterns(List.of("data"));
        params.setExcludePatterns(List.of("skip"));

        ResultType result = transform(params, """
                data: keep
                data: skip this
                data: also keep
                ignore
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        data: keep
                        data: also keep
                        """);
    }

    @Test
    void allOperationsCombined() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setSkipLines(1);      // Skip header
        params.setHeadLines(5);      // Take first 5 after skip
        params.setIncludePatterns(List.of("INFO", "ERROR"));  // Keep INFO or ERROR lines
        params.setExcludePatterns(List.of("DEBUG"));  // Remove DEBUG lines
        params.setKeepEmpty(false);  // Remove empty lines

        ResultType result = transform(params, """
                HEADER
                INFO: first

                ERROR: second
                DEBUG INFO: skip
                INFO: third
                WARNING: fourth
                INFO: fifth
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        INFO: first
                        ERROR: second
                        INFO: third
                        """);
    }

    // --- Edge Cases ---

    @Test
    void emptyContent() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(5);

        ResultType result = transform(params, "");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "");
    }

    @Test
    void singleLineNoTrailingDelimiter() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(1);

        ResultType result = transform(params, "single line");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "single line");
    }

    @Test
    void contentWithoutTrailingNewline() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(2);

        // Text block without trailing newline (closing """ on same line as last content)
        ResultType result = transform(params, """
                line1
                line2
                line3""");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2""");
    }

    @Test
    void tailLinesWithoutTrailingNewline() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setTailLines(2);

        ResultType result = transform(params, """
                line1
                line2
                line3
                line4""");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line3
                        line4""");
    }

    @Test
    void skipLinesWithoutTrailingNewline() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setSkipLines(2);

        ResultType result = transform(params, """
                header1
                header2
                data1
                data2""");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        data1
                        data2""");
    }

    @Test
    void includePatternsWithoutTrailingNewline() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setIncludePatterns(List.of("ERROR"));

        ResultType result = transform(params, """
                INFO: started
                ERROR: failed
                INFO: retrying
                ERROR: timeout""");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        ERROR: failed
                        ERROR: timeout""");
    }

    @Test
    void excludePatternsWithoutTrailingNewline() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setExcludePatterns(List.of("^#"));

        ResultType result = transform(params, """
                # comment
                key=value
                # another comment
                foo=bar""");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        key=value
                        foo=bar""");
    }

    @Test
    void headAndTailUnionWithoutTrailingNewline() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(2);
        params.setTailLines(2);

        ResultType result = transform(params, """
                line1
                line2
                line3
                line4
                line5""");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        line4
                        line5""");
    }

    @Test
    void customDelimiterWithoutTrailingDelimiter() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setLineDelimiter("|");
        params.setHeadLines(2);

        ResultType result = transform(params, "line1|line2|line3|line4");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "line1|line2");
    }

    @Test
    void allOperationsWithoutTrailingNewline() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setSkipLines(1);
        params.setHeadLines(4);
        params.setIncludePatterns(List.of("data"));
        params.setKeepEmpty(false);

        ResultType result = transform(params, """
                HEADER
                data: first

                data: second
                noise
                data: third""");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        data: first
                        data: second""");
    }

    @Test
    void noParametersPassesThrough() {
        FilterLinesParameters params = new FilterLinesParameters();

        ResultType result = transform(params, """
                line1
                line2
                line3
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        line3
                        """);
    }

    @Test
    void negativeHeadLinesPassesThrough() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(-1);

        ResultType result = transform(params, """
                line1
                line2
                line3
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        line3
                        """);
    }

    @Test
    void negativeTailLinesPassesThrough() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setTailLines(-1);

        ResultType result = transform(params, """
                line1
                line2
                line3
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        line3
                        """);
    }

    @Test
    void negativeSkipLinesPassesThrough() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setSkipLines(-1);

        ResultType result = transform(params, """
                line1
                line2
                line3
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        line3
                        """);
    }

    @Test
    void emptyIncludePatternsListPassesThrough() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setIncludePatterns(List.of());

        ResultType result = transform(params, """
                line1
                line2
                line3
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        line3
                        """);
    }

    @Test
    void emptyExcludePatternsListPassesThrough() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setExcludePatterns(List.of());

        ResultType result = transform(params, """
                line1
                line2
                line3
                """);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        line3
                        """);
    }

    // --- Error Cases ---

    @Test
    void invalidIncludePatternThrowsAtConstruction() {
        FilterLinesParameters params = new FilterLinesParameters();

        PatternSyntaxException exception = assertThrows(PatternSyntaxException.class,
                () -> params.setIncludePatterns(List.of("[invalid")));

        assertTrue(exception.getMessage().contains("Unclosed character class"));
    }

    @Test
    void invalidExcludePatternThrowsAtConstruction() {
        FilterLinesParameters params = new FilterLinesParameters();

        PatternSyntaxException exception = assertThrows(PatternSyntaxException.class,
                () -> params.setExcludePatterns(List.of("(unclosed")));

        assertTrue(exception.getMessage().contains("Unclosed group"));
    }

    @Test
    void invalidPatternInListThrowsAtConstruction() {
        FilterLinesParameters params = new FilterLinesParameters();

        PatternSyntaxException exception = assertThrows(PatternSyntaxException.class,
                () -> params.setIncludePatterns(List.of("valid", "[invalid", "also-valid")));

        assertTrue(exception.getMessage().contains("Unclosed character class"));
    }

    @Test
    void emptyDelimiterErrors() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setLineDelimiter("");

        ResultType result = transform(params, """
                line1
                line2
                """);

        ErrorResultAssert.assertThat(result)
                .hasCause("Error transforming content at index 0")
                .hasContextContaining("lineDelimiter cannot be null or empty");
    }

    @Test
    void nullDelimiterErrors() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setLineDelimiter(null);

        ResultType result = transform(params, """
                line1
                line2
                """);

        ErrorResultAssert.assertThat(result)
                .hasCause("Error transforming content at index 0")
                .hasContextContaining("lineDelimiter cannot be null or empty");
    }

    // --- Content Selection Tests ---

    @Test
    void selectByContentIndex() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(1);
        params.setContentIndexes(List.of(1));

        ActionContent content1 = runner.saveContent("""
                a
                b
                c
                """, "first.txt", "text/plain");
        ActionContent content2 = runner.saveContent("""
                x
                y
                z
                """, "second.txt", "text/plain");
        TransformInput input = TransformInput.builder().content(List.of(content1, content2)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(0, "first.txt", "text/plain", """
                        a
                        b
                        c
                        """)
                .hasContentMatchingAt(1, "second.txt", "text/plain", """
                        x
                        """);
    }

    @Test
    void selectByMediaType() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(1);
        params.setMediaTypes(List.of("text/plain"));

        ActionContent textContent = runner.saveContent("""
                a
                b
                c
                """, "test.txt", "text/plain");
        ActionContent csvContent = runner.saveContent("""
                1
                2
                3
                """, "data.csv", "text/csv");
        TransformInput input = TransformInput.builder().content(List.of(textContent, csvContent)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        a
                        """)
                .hasContentMatchingAt(1, "data.csv", "text/csv", """
                        1
                        2
                        3
                        """);
    }

    @Test
    void retainExistingContent() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(2);
        params.setRetainExistingContent(true);

        ResultType result = transform(params, """
                line1
                line2
                line3
                """);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(0, "test.txt", "text/plain", """
                        line1
                        line2
                        line3
                        """)
                .hasContentMatchingAt(1, "test.txt", "text/plain", """
                        line1
                        line2
                        """);
    }

    @Test
    void selectByFilePattern() {
        FilterLinesParameters params = new FilterLinesParameters();
        params.setHeadLines(1);
        params.setFilePatterns(List.of("*.log"));

        ActionContent logContent = runner.saveContent("""
                log1
                log2
                log3
                """, "app.log", "text/plain");
        ActionContent txtContent = runner.saveContent("""
                txt1
                txt2
                txt3
                """, "data.txt", "text/plain");
        TransformInput input = TransformInput.builder().content(List.of(logContent, txtContent)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(0, "app.log", "text/plain", """
                        log1
                        """)
                .hasContentMatchingAt(1, "data.txt", "text/plain", """
                        txt1
                        txt2
                        txt3
                        """);
    }

    private ResultType transform(FilterLinesParameters params, String content) {
        ActionContent actionContent = runner.saveContent(content, "test.txt", "text/plain");
        TransformInput input = TransformInput.builder().content(List.of(actionContent)).build();
        return action.transform(runner.actionContext(), params, input);
    }
}
