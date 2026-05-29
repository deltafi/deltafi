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
package org.deltafi.core.action.split;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.asserters.TransformResultsAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class LineSplitTest {
    private static final String LINE1 = "1Aa";
    private static final String LINE2 = "2Bb";
    private static final String LINE3 = "3Cc";
    private static final String LINE4 = "4Dd";
    private static final String LINE5 = "5Ee";
    private static final String CR_LF = "\r\n";

    private static final String NO_TRAILING_NEWLINE = LINE1 + "\n\n" + LINE2;

    private static final String TRAILING_NEWLINE = NO_TRAILING_NEWLINE + "\n";

    private static final String THREE_CR_LF_NO_TRAILING = LINE1 + CR_LF + LINE2 + CR_LF + LINE3;

    private static final String FIVE_LINES = LINE1 + "\n" + LINE2 + "\n" + LINE3 + "\n" + LINE4 + "\n" + LINE5;

    LineSplit action = new LineSplit();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void noTrailingNewLine() {
        checkTransformResult(NO_TRAILING_NEWLINE, List.of(LINE1, LINE2), true);
    }

    @Test
    void hasNoNewlines() {
        checkTransformResult(LINE1, List.of(LINE1), true);
    }

    @Test
    void trailingNewline() {
        checkTransformResult(LINE1 + "\n", List.of(LINE1), true);
    }

    @Test
    void leadingNewLine() {
        checkTransformResult("\n" + LINE1, List.of(LINE1), true);
    }

    @Test
    void newlineBeforeAndAfter() {
        checkTransformResult("\n" + LINE1 + "\n", List.of(LINE1), true);
    }

    @Test
    void singleWithCrAndLf() {
        checkTransformResult(LINE1 + "\r\n", List.of(LINE1), true);
    }

    @Test
    void threeCrLfNoTrailing() {
        checkTransformResult(THREE_CR_LF_NO_TRAILING, List.of(LINE1, LINE2, LINE3), true);
    }

    @Test
    void threeCrLfWithTrailing() {
        checkTransformResult(THREE_CR_LF_NO_TRAILING + CR_LF, List.of(LINE1, LINE2, LINE3), true);
    }

    @Test
    void threeWithCrLfBeforeAndAfter() {
        checkTransformResult(CR_LF + THREE_CR_LF_NO_TRAILING + CR_LF, List.of(LINE1, LINE2, LINE3), true);
    }

    @Test
    void skipBlanks() {
        checkTransformResult(TRAILING_NEWLINE, List.of(LINE1, LINE2), true);
    }

    @Test
    void doNotSkipBlankLines() {
        checkTransformResult(TRAILING_NEWLINE, List.of(LINE1, "", LINE2), false);
    }

    @Test
    void mixTest() {
        checkTransformResult(CR_LF + CR_LF + LINE1 + "\n  \n" + LINE2 + CR_LF + "\n" + LINE3 + "\n \n", List.of(LINE1, LINE2, LINE3), true);
    }

    @Test
    void mixTestNoSkipBlankLines() {
        checkTransformResult(CR_LF + CR_LF + LINE1 + "\n  \n" + LINE2 + CR_LF + "\n" + LINE3 + "\n \n", List.of("", "", LINE1, "  ", LINE2, "", LINE3, " "), false);
    }

    @Test
    void splitOneContentPerChild() {
        checkChildResults(FIVE_LINES, List.of(LINE1, LINE2, LINE3, LINE4, LINE5), 5, 1);
    }

    @Test
    void splitTenPerChild() {
        checkChildResults(FIVE_LINES, List.of(LINE1, LINE2, LINE3, LINE4, LINE5), 1, 10);
    }

    @Test
    void splitOneChildOneLine() {
        checkChildResults(LINE1, List.of(LINE1), 1, 10);
    }

    @Test
    void splitTwoContentsPerChild() {
        checkChildResults(FIVE_LINES, List.of(LINE1, LINE2, LINE3, LINE4, LINE5), 3, 2);
    }

    private void checkTransformResult(String inputContent, List<String> expectedOutput, boolean skipBlankLines) {
        LineSplitParameters params = new LineSplitParameters();
        params.setSkipBlankLines(skipBlankLines);
        params.setSplitIntoChildResults(false);

        String inputName = "input.txt";
        ActionContent content = runner.saveContent(inputContent, inputName, "text/plain");
        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert transformResultAssert = TransformResultAssert.assertThat(result);
        transformResultAssert.hasContentCount(expectedOutput.size());
        for (int index = 0; index < expectedOutput.size(); ++index) {
            transformResultAssert.hasContentMatchingAt(index, inputName, MediaType.TEXT_PLAIN, expectedOutput.get(index));
        }
    }

    private void checkChildResults(String inputContent, List<String> expectedOutput, int expectedChildCount, int maxPerChild) {
        LineSplitParameters params = new LineSplitParameters();
        params.setSkipBlankLines(true);
        params.setSplitIntoChildResults(true);
        params.setMaxContentPerChild(maxPerChild);

        String inputName = "input.txt";
        ActionContent content = runner.saveContent(inputContent, inputName, "text/plain");
        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultsAssert transformResultsAssert = TransformResultsAssert.assertThat(result);
        transformResultsAssert.hasChildrenSize(expectedChildCount);

        int checkPos = 0;
        for (int childIndex = 0; childIndex < expectedChildCount; ++childIndex) {
            for (int contentIndex = 0; contentIndex < maxPerChild; contentIndex++) {
                transformResultsAssert.hasChildResultAt(childIndex, contentIndex, inputName, "text/plain", expectedOutput.get(checkPos), Map.of(), Map.of(), "child");
                ++checkPos;
                if (checkPos >= expectedOutput.size()) break;
            }
        }
        assertEquals(checkPos, expectedOutput.size());
    }
}