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
package org.deltafi.core.action.metadata;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.ErrorResultAssert;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

class ValidateMetadataTest {

    private static final Map<String, String> REQUIRED_METADATA = Map.of(
            "anything", ".*",
            "dontCare", "",
            "good", "",
            "startWithA", "A.*",
            "twoDigits", "\\d{2}");

    private static final Map<String, String> GOOD_METADATA = Map.of(
            "extraKey", "isIgnored",
            "anything", "123abc",
            "dontCare", " spaces ",
            "good", "news",
            "startWithA", "ABC",
            "twoDigits", "12");

    ValidateMetadata action = new ValidateMetadata();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void testGoodTransform() {
        ValidateMetadataParameters params = new ValidateMetadataParameters();
        params.setRequiredMetadata(REQUIRED_METADATA);

        TransformInput input = TransformInput.builder()
                .content(List.of(saveContent()))
                .metadata(GOOD_METADATA)
                .build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        TransformResultAssert.assertThat(result).hasContentCount(1);
    }

    @Test
    void testNothingRequiredPassthrough() {
        ValidateMetadataParameters params = new ValidateMetadataParameters();
        params.setRequiredMetadata(Collections.emptyMap());

        TransformInput input = TransformInput.builder()
                .content(List.of(saveContent()))
                .metadata(GOOD_METADATA)
                .build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        TransformResultAssert.assertThat(result).hasContentCount(1);
    }

    @Test
    void testAllRequiredIsMissing() {
        ValidateMetadataParameters params = new ValidateMetadataParameters();
        params.setRequiredMetadata(REQUIRED_METADATA);

        TransformInput input = TransformInput.builder()
                .content(List.of(saveContent()))
                .metadata(Collections.emptyMap())
                .build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        ErrorResultAssert.assertThat(result)
                .hasCause("Required metadata is missing or invalid")
                .hasContext("Missing required keys: anything, dontCare, good, startWithA, twoDigits\n");
    }

    @Test
    void testBothMissingAndInvalid() {
        ValidateMetadataParameters params = new ValidateMetadataParameters();
        params.setRequiredMetadata(REQUIRED_METADATA);

        TransformInput input = TransformInput.builder()
                .content(List.of(saveContent()))
                .metadata(Map.of(
                        "extraKey", "isIgnored",
                        "good", "news",
                        "startWithA", "abc",
                        "twoDigits", "1234"))
                .build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        ErrorResultAssert.assertThat(result)
                .hasCause("Required metadata is missing or invalid")
                .hasContext("Missing required keys: anything, dontCare\n" +
                        "Invalid metadata:\n" +
                        "- twoDigits: 1234 (does not match \\d{2})\n" +
                        "- startWithA: abc (does not match A.*)");
    }

    private ActionContent saveContent() {
        return runner.saveContent("content", "name", "text/plan");
    }

}
