/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.delete;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;

public class DeleteContentTest {
    DeleteContent action = new DeleteContent();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();
    ActionContext context = runner.actionContext();

    @Test
    void testDeleteAll() {
        DeleteContentParameters params = new DeleteContentParameters();
        params.setDeleteAllContent(true);

        TransformInput input = TransformInput.builder()
                .content(List.of(
                        runner.saveEmptyContent("file0.jpg", "image/jpeg"),
                        runner.saveEmptyContent("file1.mp4", "video/mp4"),
                        runner.saveEmptyContent("file2.png", "image/png")))
                .build();
        ResultType result = action.transform(context, params, input);

        assertTransformResult(result).hasContentCount(0);
    }
}
