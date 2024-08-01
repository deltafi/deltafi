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
package org.deltafi.core.action.metadata;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;

class MetadataToContentTest {
    MetadataToContent action = new MetadataToContent();
    DeltaFiTestRunner testRunner = DeltaFiTestRunner.setup();
    ActionContext context = testRunner.actionContext();

    @Test
    void testMetadataToContentWithAllMetadata() {
        ResultType result = action.transform(context, new MetadataToContentParameters(), createInput());

        assertTransformResult(result)
                .hasContentCount(2) // Original content plus metadata
                .hasContentMatchingAt(1, this::checkContent);
    }

    @Test
    void testMetadataToContentWithPatternFilter() {
        MetadataToContentParameters params = new MetadataToContentParameters();
        params.setMetadataPatterns(List.of("key\\d+"));

        ResultType result = action.transform(context, params, createInput());

        assertTransformResult(result)
                .hasContentCount(2)
                .hasContentMatchingAt(1, content -> {
                    Assertions.assertThat(content.getName()).isEqualTo("metadata.json");
                    String loadedContent = content.loadString();
                    Assertions.assertThat(loadedContent).contains("\"key1\":", "\"key2\":");
                    Assertions.assertThat(loadedContent).doesNotContain("\"unmatched\":");
                    return true;
                });
    }

    @Test
    void testMetadataToContentReplaceExistingContent() {
        MetadataToContentParameters params = new MetadataToContentParameters();
        params.setReplaceExistingContent(true);

        ResultType result = action.transform(context, params, createInput());

        assertTransformResult(result)
                .hasContentCount(1) // Only metadata, original content replaced
                .hasContentMatchingAt(0, this::checkContent);
    }

    private boolean checkContent(ActionContent content) {
        Assertions.assertThat(content.getName()).isEqualTo("metadata.json");
        String loadedContent = content.loadString();
        Assertions.assertThat(loadedContent).contains("\"key1\":", "\"key2\":", "\"unmatched\":");
        return true;
    }

    private TransformInput createInput() {
        ActionContent content = testRunner.saveContent("{\"data\": \"value\"}", "example.json", "application/json");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        metadata.put("key2", "value2");
        metadata.put("unmatched", "value3");
        return TransformInput.builder().content(List.of(content)).metadata(metadata).build();
    }
}
