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
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.format.FormatInput;
import org.deltafi.passthrough.param.RoteParameters;
import org.deltafi.test.asserters.ActionResultAssertions;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

class RoteFormatActionTest {


    RoteFormatAction action = new RoteFormatAction();
    DeltaFiTestRunner deltaFiTestRunner = DeltaFiTestRunner.setup(action);

    @Test
    void testDefault() {
        ActionContent fileOne = deltaFiTestRunner.saveContent("input 1", "file1", "text/plain");
        ActionContent fileTwo = deltaFiTestRunner.saveContent("input 2", "file2", "text/plain");

        FormatInput input = FormatInput.builder()
                .content(List.of(fileOne, fileTwo))
                .build();

        ResultType result = action.format(deltaFiTestRunner.actionContext(), new RoteParameters(), input);

        // RoteFormat only keeps the first content, verify the formatted content matches data from file1
        ActionResultAssertions.assertFormatResult(result)
                        .hasFormattedContent("file1", "input 1", "text/plain");
    }
}
