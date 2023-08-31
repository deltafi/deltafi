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
package org.deltafi.core.action;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.core.parameters.ExtractJsonMetadataParameters;
import org.deltafi.core.parameters.HandleMultipleKeysType;
import org.deltafi.test.action.transform.TransformActionTest;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.deltafi.test.asserters.ActionResultAssertions.assertErrorResult;
import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;

class ExtractJsonMetadataTransformActionTest extends TransformActionTest {

    ExtractJsonMetadataTransformAction action = new ExtractJsonMetadataTransformAction();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action);

    @Test
    void testNestedJson() {
        ExtractJsonMetadataParameters params = new ExtractJsonMetadataParameters();
        params.setJsonPathToMetadataKeysMap(Map.of("$.person.name", "nameMetadata", "$.person.contact.email", "emailMetadata"));

        ActionContent content = saveJson("{\"person\":{\"name\":\"John\",\"contact\":{\"email\":\"john@example.com\"}}}");
        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedMetadataEquals(Map.of("nameMetadata", "John", "emailMetadata", "john@example.com"));
    }

    @Test
    void testHandleMultipleKeysFirst() {
        testMultipleKeysPolicy(HandleMultipleKeysType.FIRST, "firstValue");
    }

    @Test
    void testHandleMultipleKeysLast() {
        testMultipleKeysPolicy(HandleMultipleKeysType.LAST, "lastValue");
    }

    @Test
    void testHandleMultipleKeysAll() {
        testMultipleKeysPolicy(HandleMultipleKeysType.ALL, "firstValue,middleValue,lastValue");
    }

    private void testMultipleKeysPolicy(HandleMultipleKeysType policy, String expectedValue) {
        ExtractJsonMetadataParameters params = new ExtractJsonMetadataParameters();
        params.setHandleMultipleKeys(policy);
        params.setJsonPathToMetadataKeysMap(Map.of("$.values[*]", "valuesMetadata"));

        ActionContent content = saveJson("{\"values\":[\"firstValue\",\"middleValue\",\"lastValue\"]}");
        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedMetadataEquals(Map.of("valuesMetadata", expectedValue));
    }

    @Test
    void testKeysInMultipleFiles() {
        ExtractJsonMetadataParameters params = new ExtractJsonMetadataParameters();
        params.setJsonPathToMetadataKeysMap(Map.of("$.value", "valuesMetadata"));
        params.setAllKeysDelimiter("|");

        ActionContent content = saveJson("{\"value\":\"firstValue\"}");
        ActionContent content2 = saveJson("{\"value\":\"nextValue\"}");
        TransformInput input = TransformInput.builder().content(List.of(content, content2)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedMetadataEquals(Map.of("valuesMetadata", "firstValue|nextValue"));
    }

    @Test
    void testMediaTypes() {
        ExtractJsonMetadataParameters params = new ExtractJsonMetadataParameters();
        params.setJsonPathToMetadataKeysMap(Map.of("$.value", "valuesMetadata"));
        params.setMediaTypes(List.of("application/json", "nonstandard/*"));

        ActionContent content = saveJson("{\"value\":\"firstValue\"}");
        ActionContent content2 = ActionContent.saveContent(runner.actionContext(), "{\"value\":\"nextValue\"}", "example2.json","nonstandard/json");
        ActionContent content3 = ActionContent.saveContent(runner.actionContext(), "{\"value\":\"ignoredValue\"}", "example3.json","application/somethingElse");

        TransformInput input = TransformInput.builder().content(List.of(content, content2, content3)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedMetadataEquals(Map.of("valuesMetadata", "firstValue,nextValue"));
    }

    @Test
    void testFilePatterns() {
        ExtractJsonMetadataParameters params = new ExtractJsonMetadataParameters();
        params.setJsonPathToMetadataKeysMap(Map.of("$.value", "valuesMetadata"));
        params.setFilePatterns(List.of("example*", "*.json"));

        ActionContent content = ActionContent.saveContent(runner.actionContext(), "{\"value\":\"1\"}", "example1.txt","application/json");
        ActionContent content2 = ActionContent.saveContent(runner.actionContext(), "{\"value\":\"2\"}", "example2.json","application/json");
        ActionContent content3 = ActionContent.saveContent(runner.actionContext(), "{\"value\":\"3\"}", "another.json","application/json");
        ActionContent content4 = ActionContent.saveContent(runner.actionContext(), "{\"value\":\"4\"}", "ignored.txt","application/json");

        TransformInput input = TransformInput.builder().content(List.of(content, content2, content3, content4)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedMetadata("valuesMetadata", "1,2,3");
    }

    @Test
    void testContentIndexes() {
        ExtractJsonMetadataParameters params = new ExtractJsonMetadataParameters();
        params.setJsonPathToMetadataKeysMap(Map.of("$.value", "valuesMetadata"));
        params.setContentIndexes(List.of(0, 2));

        ActionContent content = saveJson("{\"value\":\"firstValue\"}");
        ActionContent content2 = saveJson("{\"value\":\"ignoredValue\"}");
        ActionContent content3 = saveJson("{\"value\":\"lastValue\"}");

        TransformInput input = TransformInput.builder().content(List.of(content, content2, content3)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedMetadataEquals(Map.of("valuesMetadata", "firstValue,lastValue"));
    }

    @Test
    void testErrorOnKeyNotFound() {
        ExtractJsonMetadataParameters params = new ExtractJsonMetadataParameters();
        params.setJsonPathToMetadataKeysMap(Map.of("$.missingKey", "missingMetadata"));
        params.setErrorOnKeyNotFound(true);

        ActionContent content = saveJson("{\"key\":\"value\"}");

        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertErrorResult(result).hasCause("Key not found: $.missingKey");
    }



    @Test
    void testNoErrorOnKeyNotFound() {
        ExtractJsonMetadataParameters params = new ExtractJsonMetadataParameters();
        params.setJsonPathToMetadataKeysMap(Map.of("$.missingKey", "missingMetadata"));
        params.setErrorOnKeyNotFound(false);

        ActionContent content = saveJson("{\"key\":\"value\"}");

        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedMetadataEquals(Collections.emptyMap());
    }

    private ActionContent saveJson(String json) {
        return ActionContent.saveContent(runner.actionContext(), json, "example.json","application/json");
    }
}
