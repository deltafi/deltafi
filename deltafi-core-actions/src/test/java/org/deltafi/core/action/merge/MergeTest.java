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
package org.deltafi.core.action.merge;

import org.bouncycastle.util.Arrays;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;

public class MergeTest {
    @Test
    public void merges() {
        Merge merge = new Merge();
        DeltaFiTestRunner runner = DeltaFiTestRunner.setup("MergeTest");

        MergeParameters mergeParameters = new MergeParameters();
        mergeParameters.setMergedFilename("merged");
        mergeParameters.setMediaType(MediaType.TEXT_PLAIN);

        TransformResultType transformResultType = merge.transform(runner.actionContext(),
                mergeParameters, TransformInput.builder().content(
                        runner.saveContentFromResource("thing1.txt", "thing2.txt")).build());

        byte[] expectedOutput = Arrays.concatenate(runner.readResourceAsBytes("thing1.txt"),
                runner.readResourceAsBytes("thing2.txt"));
        TransformResultAssert.assertThat(transformResultType)
                .hasContentMatchingAt(0, "merged", MediaType.TEXT_PLAIN, expectedOutput);
    }
}
