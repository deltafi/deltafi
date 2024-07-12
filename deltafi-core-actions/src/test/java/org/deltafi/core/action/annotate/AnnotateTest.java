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
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.ActionResultAssertions;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;


class AnnotateTest {

    Annotate action = new Annotate();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void addsAnnotations() {
        AnnotateParameters params = new AnnotateParameters();
        params.setAnnotations(new HashMap<>(Map.of("key1", "value1", "key2", "value2")));

        TransformInput input = TransformInput.builder().build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        ActionResultAssertions.assertTransformResult(result)
                .addedAnnotations(Map.of("key1", "value1", "key2", "value2"));
    }

    @Test
    void errorsOnBlankKey() {
        AnnotateParameters params = new AnnotateParameters();
        params.setAnnotations(new HashMap<>(Map.of("key1", "value1", "", "value2")));

        TransformInput input = TransformInput.builder().build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        ActionResultAssertions.assertErrorResult(result)
                .hasCause("Invalid annotations")
                .hasContext("Contains a blank key");
    }

    @Test
    void errorsOnBlankValue() {
        AnnotateParameters params = new AnnotateParameters();
        params.setAnnotations(new HashMap<>(Map.of("key1", "", "key2", "value2")));

        TransformInput input = TransformInput.builder().build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        ActionResultAssertions.assertErrorResult(result)
                .hasCause("Invalid annotations")
                .hasContext("Annotations with the following keys were invalid: key1");
    }

    @Test
    void addsAnnotationsFromMetadata() {
        AnnotateParameters params = new AnnotateParameters();
        params.setMetadataPatterns(List.of(".*"));

        ResultType result = action.transform(runner.actionContext(), params, createInput());

        assertTransformResult(result)
                .hasContentCount(1)
                .addedAnnotations(Map.of(
                        "prefix.key1", "value1",
                        "prefix.key2", "value2",
                        "prefix.other", "value3",
                        "other.key4", "value4",
                        "first.prefix.second", "value5")
                );
    }

    @Test
    void addsAnnotationsFromMetadataFiltered() {
        AnnotateParameters params = new AnnotateParameters();
        params.setMetadataPatterns(List.of("prefix\\.key\\d+"));

        ResultType result = action.transform(runner.actionContext(), params, createInput());

        assertTransformResult(result)
                .hasContentCount(1)
                .addedAnnotations(Map.of(
                        "prefix.key1", "value1",
                        "prefix.key2", "value2")
                );
    }

    @Test
    void addsAnnotationsFromMetadataDiscardingPrefix() {
        AnnotateParameters params = new AnnotateParameters();
        params.setMetadataPatterns(List.of(".*"));
        params.setDiscardPrefix("prefix.");

        ResultType result = action.transform(runner.actionContext(), params, createInput());

        assertTransformResult(result)
                .hasContentCount(1)
                .addedAnnotations(Map.of(
                        "key1", "value1",
                        "key2", "value2",
                        "other", "value3",
                        "other.key4", "value4",
                        "first.prefix.second", "value5")
                );
    }

    @Test
    void addsAnnotationsFromMetadataFilteredDiscardingPrefix() {
        AnnotateParameters params = new AnnotateParameters();
        params.setMetadataPatterns(List.of("prefix\\.key\\d+"));
        params.setDiscardPrefix("prefix.");

        ResultType result = action.transform(runner.actionContext(), params, createInput());

        assertTransformResult(result)
                .hasContentCount(1)
                .addedAnnotations(Map.of(
                        "key1", "value1",
                        "key2", "value2")
                );
    }

    private TransformInput createInput() {
        ActionContent content = runner.saveContent("{\"data\": \"value\"}", "example.json", "application/json");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("prefix.key1", "value1");
        metadata.put("prefix.key2", "value2");
        metadata.put("prefix.other", "value3");
        metadata.put("other.key4", "value4");
        metadata.put("first.prefix.second", "value5");
        return TransformInput.builder().content(List.of(content)).metadata(metadata).build();
    }
}
