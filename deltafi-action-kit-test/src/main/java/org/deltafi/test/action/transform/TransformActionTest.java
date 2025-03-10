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
package org.deltafi.test.action.transform;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.test.action.ActionTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

/**
 * @deprecated Use the DeltaFiTestRunner to set up the test and run the action directly.
 * The result can be verified using {@link org.deltafi.test.asserters.ActionResultAssertions}.
 */
@Deprecated
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

        List<byte[]> expectedContent = getExpectedContentOutput(expectedResult, testCase, testCase.getOutputs());

        expectedResult.addMetadata(testCase.getResultMetadata());
        expectedResult.setDeleteMetadataKeys(testCase.getResultDeleteMetadataKeys());
        ActionEvent actualEvent = normalizeEvent(result.toEvent());
        ActionEvent expectedEvent = normalizeEvent(expectedResult.toEvent());
        Assertions.assertEquals(expectedEvent, actualEvent);

        List<byte[]> actualContent = result.getContent().stream().map(ActionContent::loadBytes).toList();

        assertContentIsEqual(expectedContent, actualContent);
    }

}
