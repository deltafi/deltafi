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
import org.deltafi.actionkit.action.ingress.IngressResult;
import org.deltafi.actionkit.action.ingress.IngressResultItem;
import org.deltafi.common.types.IngressStatus;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Assertions for IngressResults
 */
public class IngressResultAssert extends ResultAssert<IngressResultAssert, IngressResult> {
    private IngressResultAssert(IngressResult ingressResult) {
        super(ingressResult, IngressResultAssert.class);
    }

    /**
     * Create a new IngressResultAssert with the given result, asserting that the result is not null and an instance
     * of IngressResult.
     *
     * @param result to validate
     * @return a new IngressResultAssert
     */
    public static IngressResultAssert assertThat(ResultType result) {
        return assertThat(result, "Is non-null IngressResult");
    }

    /**
     * Create a new IngressResultAssert with the given result, asserting that the result is not null and an instance
     * of IngressResult.
     *
     * @param result      to validate
     * @param description a description to include with the not null and instance of assertions
     * @return a new IngressResultAssert
     */
    public static IngressResultAssert assertThat(ResultType result, String description) {
        ResultAssertions.assertNonNullResult(result, IngressResult.class, description);
        return new IngressResultAssert((IngressResult) result);
    }

    /**
     * Verify that the IngressResult list has a size equal to the given count.
     *
     * @param count expected size
     * @return this
     */
    public IngressResultAssert hasChildrenSize(int count) {
        return hasChildrenSize(count, "Has " + count + " child results");
    }

    /**
     * Verify that the IngressResult list has a size equal to the given count.
     *
     * @param count       expected size
     * @param description a description to include with the assertion (may be null)
     * @return this
     */
    public IngressResultAssert hasChildrenSize(int count, String description) {
        Assertions.assertThat(actual.getIngressResultItems()).describedAs(description).hasSize(count);
        return myself;
    }

    /**
     * Verify that the IngressResult list has a memo equal to the given value.
     *
     * @param memo expected memo value
     * @return this
     */
    public IngressResultAssert hasMemoValue(String memo) {
        return hasMemoValue(memo, "Memo value matches");
    }

    /**
     * Verify that the IngressResult list has a memo equal to the given value.
     *
     * @param memo        expected memo value
     * @param description a description to include with the assertion (may be null)
     * @return this
     */
    public IngressResultAssert hasMemoValue(String memo, String description) {
        Assertions.assertThat(actual.getMemo()).describedAs(description).isEqualTo(memo);
        return myself;
    }

    /**
     * Runs the provided match predicate against the child IngressResultItem at the specified index
     * for the specified content position.
     *
     * @param childIndex   position of the IngressResultItem in the child list
     * @param contentIndex position of the Content in the content list
     * @param contentName  the expected content name
     * @param mediaType    the expected media type
     * @param content      the expected content
     * @param metadata     the expected metadata
     * @param annotations  the expected annotations
     * @param description  a description to include with the assertion
     * @return this
     */
    public IngressResultAssert hasChildResultAt(int childIndex, int contentIndex, String contentName, String mediaType, String content,
                                                Map<String, String> metadata, Map<String, String> annotations, String description) {
        if (actual.getIngressResultItems() == null || childIndex >= actual.getIngressResultItems().size()) {
            String numChildren = actual.getIngressResultItems() == null ? "child list is null" :
                    "child list has size " + actual.getIngressResultItems().size();
            describedAs(description);
            failWithMessage("There is no child at index %s (%s)", childIndex, numChildren);
            return myself;
        }

        IngressResultItem actualChild = actual.getIngressResultItems().get(childIndex);
        if (actualChild.getContent() == null || contentIndex >= actualChild.getContent().size()) {
            String contentSize = actualChild.getContent() == null ? "content list is null" :
                    "content list has size " + actualChild.getContent().size();
            describedAs(description);
            failWithMessage("There is no content at index %s (%s)", contentIndex, contentSize);
            return myself;
        }

        SoftAssertions softAssertions = new SoftAssertions();
        buildIngressResultItemAsserter(contentIndex, contentName, mediaType, content, metadata, annotations)
                .accept(actualChild, softAssertions);
        try {
            softAssertions.assertAll();
        } catch (AssertionError e) {
            describedAs(description);
            failWithMessage("\nChild/content at index %d/%d failed %s", childIndex, contentIndex, e.getMessage());
        }
        return myself;
    }

    /**
     * Verify that the IngressResult status is equal to the given value.
     *
     * @param expectedStatus expected status value
     * @return this
     */
    public IngressResultAssert hasStatus(IngressStatus expectedStatus) {
        return hasStatus(expectedStatus, "IngressResult status is equal to");
    }

    /**
     * Verify that the IngressResult status is equal to the given value.
     *
     * @param expectedStatus expected status value
     * @param description a description to include with the assertion (may be null)
     * @return this
     */
    public IngressResultAssert hasStatus(IngressStatus expectedStatus, String description) {
        Assertions.assertThat(actual.getStatus()).describedAs(description).isEqualTo(expectedStatus);
        return myself;
    }

    /**
     * Verify that the IngressResult status message is equal to the given value.
     *
     * @param expectedStatusMessage expected status message
     * @return this
     */
    public IngressResultAssert hasStatusMessage(String expectedStatusMessage) {
        return hasStatusMessage(expectedStatusMessage, "IngressResult status message is equal to");
    }

    /**
     * Verify that the IngressResult status message is equal to the given value.
     *
     * @param expectedStatusMessage expected status message
     * @param description a description to include with the assertion (may be null)
     * @return this
     */
    public IngressResultAssert hasStatusMessage(String expectedStatusMessage, String description) {
        Assertions.assertThat(actual.getStatusMessage()).describedAs(description).isEqualTo(expectedStatusMessage);
        return myself;
    }

    private BiConsumer<IngressResultItem, SoftAssertions> buildIngressResultItemAsserter(int index, String contentName, String mediaType,
                                                                                         String content, Map<String, String> metadata, Map<String, String> annotations) {
        return (ingressResultItem, softAssertions) -> {
            softAssertions.assertThat(ingressResultItem.getContent().get(index).getName()).describedAs("Content name matches").isEqualTo(contentName);
            softAssertions.assertThat(ingressResultItem.getContent().get(index).getMediaType()).describedAs("Media type matches").isEqualTo(mediaType);
            softAssertions.assertThat(ingressResultItem.getContent().get(index).loadString()).describedAs("Content matches").isEqualTo(content);
            softAssertions.assertThat(ingressResultItem.getMetadata()).describedAs("Metadata matches").isEqualTo(metadata);
            softAssertions.assertThat(ingressResultItem.getAnnotations()).describedAs("Annotations match").isEqualTo(annotations);
        };
    }
}
