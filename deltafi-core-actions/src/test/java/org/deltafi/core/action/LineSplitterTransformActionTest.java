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
package org.deltafi.core.action;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.LineSplitterParameters;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.deltafi.test.asserters.ActionResultAssertions.*;

class LineSplitterTransformActionTest {

    LineSplitterTransformAction action = new LineSplitterTransformAction();
    DeltaFiTestRunner deltaFiTestRunner = DeltaFiTestRunner.setup(action, "LineSplitterTransformActionTest");
    ActionContext context = deltaFiTestRunner.actionContext();

    @Test
    void testCommentsAndHeader() {
        LineSplitterParameters params = LineSplitterParameters.builder().includeHeaderInAllChunks(true).commentChars("#").maxRows(1).build();

        ResultType result = action.transform(context, params, buildInput("commentsAndHeader"));

        assertTransformResult(result)
                .hasContentCount(3)
                .contentLoadStringEquals(getExpectedContent("commentsAndHeader", 3));
    }

    @Test
    void testDontIncludeHeader() {
        LineSplitterParameters params = LineSplitterParameters.builder().includeHeaderInAllChunks(false).commentChars("#").maxRows(1).build();

        ResultType result = action.transform(context, params, buildInput("dontIncludeHeader"));

        assertTransformResult(result)
                .contentLoadStringEquals(getExpectedContent("dontIncludeHeader", 3));
    }

    @Test
    void testHeaderExceedsMaxSize() {
        TransformInput transformInput = TransformInput.builder()
                .content(deltaFiTestRunner.saveContent("overflow"))
                .build();

        ResultType result = action.transform(context, LineSplitterParameters.builder().maxSize(1).build(), transformInput);

        assertErrorResult(result)
                .hasCauseLike(".*The current line will not fit within the max size limit.*");
    }

    @Test
    void testHeaderOnly() {
        LineSplitterParameters params = LineSplitterParameters.builder().includeHeaderInAllChunks(true).commentChars("#").maxRows(1).build();

        ResultType result = action.transform(context, params, buildInput("headerOnly"));

        assertTransformResult(result)
                .contentLoadStringEquals(getExpectedContent("headerOnly", 1));
    }

    TransformInput buildInput(String folder) {
        return TransformInput.builder()
                .content(deltaFiTestRunner.saveContentFromResource(folder + "/input.content"))
                .build();
    }

    List<String> getExpectedContent(String folder, int contentCount) {
        return IntStream.range(0, contentCount)
                .mapToObj(i -> deltaFiTestRunner.readResourceAsString(folder + "/content." + i)).toList();
    }

}