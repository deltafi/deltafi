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
package org.deltafi.core.action.annotate;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.ActionResultAssertions;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;


class AnnotateTest {

    Annotate action = new Annotate();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action);

    @Test
    void testTransform() {
        AnnotateParameters params = new AnnotateParameters();
        params.setAnnotations(new HashMap<>(Map.of("key1", "value1", "key2", "value2")));

        TransformInput input = TransformInput.builder().build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        ActionResultAssertions.assertTransformResult(result)
                .addedAnnotations(Map.of("key1", "value1", "key2", "value2"));
    }

    @Test
    void testKeyError() {
        AnnotateParameters params = new AnnotateParameters();
        params.setAnnotations(new HashMap<>(Map.of("key1", "value1", "", "value2")));

        TransformInput input = TransformInput.builder().build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        ActionResultAssertions.assertErrorResult(result)
                .hasCause("Invalid annotations")
                .hasContext("Contains a blank key");
    }

    @Test
    void testValueError() {
        AnnotateParameters params = new AnnotateParameters();
        params.setAnnotations(new HashMap<>(Map.of("key1", "", "key2", "value2")));

        TransformInput input = TransformInput.builder().build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        ActionResultAssertions.assertErrorResult(result)
                .hasCause("Invalid annotations")
                .hasContext("Key key1 contains a blank value");
    }
}
