/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.test.action.transform;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.test.action.ActionTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
@Slf4j
public abstract class TransformActionTest extends ActionTest {

    public void execute(TransformActionTestCase testCase) {
        if(testCase.getExpectedResultType()==TransformResult.class) {
            executeTransformResult(testCase);
        }
        else {
            super.execute(testCase);
        }
    }

    private void executeTransformResult(TransformActionTestCase testCase) {
        TransformResult result = execute(testCase, TransformResult.class);
        assertTransformResult(testCase, result);
    }

    private void assertTransformResult(TransformActionTestCase testCase, TransformResult result) {
        TransformResult expectedResult = new TransformResult(context());

        List<byte[]> expectedContent = getExpectedContentOutputNormalized(expectedResult, result, testCase, testCase.getOutputs());

        expectedResult.addMetadata(testCase.getResultMetadata());
        String expectedEvent = normalizeData(expectedResult.toEvent().toString());
        String outputEvent = normalizeData(result.toEvent().toString());
        Assertions.assertEquals(expectedEvent, outputEvent);

        List<byte[]> actualContent = result.getContent().stream().map(this::getContent).collect(Collectors.toList());

        assertContentIsEqual(expectedContent, actualContent);
    }

}
