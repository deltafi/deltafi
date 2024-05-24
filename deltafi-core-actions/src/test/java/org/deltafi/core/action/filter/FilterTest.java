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
package org.deltafi.core.action.filter;

import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import static org.deltafi.test.asserters.ActionResultAssertions.assertFilterResult;

class FilterTest {

    private static final ActionParameters EMPTY = new ActionParameters();

    Filter action = new Filter();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action);

    @Test
    void testTransform() {
        assertFilterResult(action.transform(runner.actionContext(), EMPTY, TransformInput.builder().build()))
                .annotationsIsEmpty()
                .hasCause("Filtered by fiat");
    }

    @Test
    void transformTest2() {
        TransformInput input = TransformInput.builder().content(runner.saveContent("some content")).build();
        assertFilterResult(action.transform(runner.actionContext(), EMPTY, input))
                .hasCause("Filtered by fiat");
    }

}