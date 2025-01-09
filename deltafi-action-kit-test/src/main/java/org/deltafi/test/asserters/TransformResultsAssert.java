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
package org.deltafi.test.asserters;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResults;

import java.util.Map;
import java.util.function.BiConsumer;

public class TransformResultsAssert extends ResultAssert<TransformResultsAssert, TransformResults> {
    /**
     * Create a new TransformResultsAssert with the given result, asserting that the result is not null and an instance
     * of TransformResults.
     * @param result to validate
     * @return a new TransformResultsAssert
     */
    public static TransformResultsAssert assertThat(ResultType result) {
        return assertThat(result, "Is non-null TransformResults");
    }

    /**
     * Create a new TransformResultsAssert with the given result, asserting that the result is not null and an instance
     * of TransformResults.
     * @param result to validate
     * @param description a description to include with the not null and instance of assertions
     * @return a new TransformResultsAssert
     */
    public static TransformResultsAssert assertThat(ResultType result, String description) {
        ResultAssertions.assertNonNullResult(result, TransformResults.class, description);
        return new TransformResultsAssert((TransformResults) result);
    }

    private TransformResultsAssert(TransformResults transformResults) {
        super(transformResults, TransformResultsAssert.class);
    }

    /**
     * Verify that the transformResults list has a size equal to the given count.
     *
     * @param count expected size
     * @return this
     */
    public TransformResultsAssert hasChildrenSize(int count) {
        return hasChildrenSize(count, "Has " + count + " child results");
    }

    /**
     * Verify that the transformResults list has a size equal to the given count.
     *
     * @param count expected size
     * @param description a description to include with the assertion (may be null)
     * @return this
     */
    public TransformResultsAssert hasChildrenSize(int count, String description) {
        Assertions.assertThat(actual.getChildResults()).describedAs(description).hasSize(count);
        return myself;
    }

    /**
     * Runs the provided match predicate against the child TransformResult at the specified index.
     *
     * @param index position of the TransformResult in the child list
     * @param contentName the expected content name
     * @param mediaType the expected media type
     * @param content the expected content
     * @param metadata the expected metadata
     * @return this
     */
    public TransformResultsAssert hasChildResultAt(int index, String contentName, String mediaType, String content,
            Map<String, String> metadata) {
        return hasChildResultAt(index, contentName, mediaType, content, metadata, "Has child result at index " + index);
    }

    /**
     * Runs the provided match predicate against the child TransformResult at the specified index.
     *
     * @param index position of the TransformResult in the child list
     * @param contentName the expected content name
     * @param mediaType the expected media type
     * @param content the expected content
     * @param metadata the expected metadata
     * @param description a description to include with the assertion
     * @return this
     */
    public TransformResultsAssert hasChildResultAt(int index, String contentName, String mediaType, String content,
            Map<String, String> metadata, String description) {
        return hasChildResultAt(index, 0, contentName, mediaType, content, metadata, null, description);
    }

    /**
     * Runs the provided match predicate against the child TransformResult at the specified index
     * for the specified content position.
     *
     * @param childIndex   position of the TransformResult in the child list
     * @param contentIndex position of the Content in the content list
     * @param contentName  the expected content name
     * @param mediaType    the expected media type
     * @param content      the expected content
     * @param metadata     the expected metadata
     * @param annotations  the expected annotations
     * @param description  a description to include with the assertion
     * @return this
     */
    public TransformResultsAssert hasChildResultAt(int childIndex, int contentIndex, String contentName, String mediaType, String content,
                                                   Map<String, String> metadata, Map<String, String> annotations, String description) {
        if (actual.getChildResults() == null || childIndex >= actual.getChildResults().size()) {
            String numChildren = actual.getChildResults() == null ? "child list is null" :
                    "child list has size " + actual.getChildResults().size();
            describedAs(description);
            failWithMessage("There is no child at index %s (%s)", childIndex, numChildren);
            return myself;
        }

        TransformResult actualChild = actual.getChildResults().get(childIndex);
        if (actualChild.getContent() == null || contentIndex >= actualChild.getContent().size()) {
            String contentSize = actualChild.getContent() == null ? "content list is null" :
                    "content list has size " + actualChild.getContent().size();
            describedAs(description);
            failWithMessage("There is no content at index %s (%s)", contentIndex, contentSize);
            return myself;
        }

        SoftAssertions softAssertions = new SoftAssertions();
        buildTransformResultAsserter(contentIndex, contentName, mediaType, content, metadata, annotations)
                .accept(actualChild, softAssertions);
        try {
            softAssertions.assertAll();
        } catch (AssertionError e) {
            describedAs(description);
            failWithMessage("\nChild/content at index %d/%d failed %s", childIndex, contentIndex, e.getMessage());
        }
        return myself;
    }

    private BiConsumer<TransformResult, SoftAssertions> buildTransformResultAsserter(int index, String contentName, String mediaType,
                                                                                     String content, Map<String, String> metadata, Map<String, String> annotations) {
        return (transformResult, softAssertions) -> {
            softAssertions.assertThat(transformResult.getContent().get(index).getName()).describedAs("Content name matches").isEqualTo(contentName);
            softAssertions.assertThat(transformResult.getContent().get(index).getMediaType()).describedAs("Media type matches").isEqualTo(mediaType);
            softAssertions.assertThat(transformResult.getContent().get(index).loadString()).describedAs("Content matches").isEqualTo(content);
            softAssertions.assertThat(transformResult.getMetadata()).describedAs("Metadata matches").isEqualTo(metadata);
            if (annotations != null) {
                softAssertions.assertThat(transformResult.getAnnotations()).describedAs("Annotations match").isEqualTo(annotations);
            }
        };
    }

}
