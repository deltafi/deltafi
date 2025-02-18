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
package org.deltafi.core.action.extract;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ContentToMetadataTest {

    ContentToMetadata action = new ContentToMetadata();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void testToMetadata() {
        ContentToMetadataParameters params = new ContentToMetadataParameters();
        params.setKey("theKey");
        params.setMaxSize(3);
        params.setContentIndexes(List.of(0));

        TransformInput input = TransformInput.builder()
                .content(List.of(
                        saveContent("1234567890", "text/plain"),
                        saveContent("Abcde", "app/other"))).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        TransformResultAssert.assertThat(result)
                .addedMetadata(Map.of("theKey", "123"))
                .hasContentCount(1)
                .hasContentMatchingAt(0, "Abc.bin",
                        "app/other", "Abcde");
    }

    @Test
    void testToAnnotation() {
        ContentToMetadataParameters params = new ContentToMetadataParameters();
        params.setKey("theKey");
        params.setRetainExistingContent(true);
        params.setExtractTarget(ExtractTarget.ANNOTATIONS);

        TransformInput input = TransformInput.builder()
                .content(List.of(
                        saveContent("1234", "text/plain"),
                        saveContent("Abcd", "app/other"))).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        TransformResultAssert.assertThat(result)
                .addedAnnotation("theKey", "1234,Abcd")
                .hasContentCount(2);
    }

    private ActionContent saveContent(String content, String mediaType) {
        String name = content.substring(0, 3);
        if (mediaType.equals("text/plan")) {
            name += ".txt";
        } else {
            name += ".bin";
        }
        return runner.saveContent(content, name, mediaType);
    }
}
