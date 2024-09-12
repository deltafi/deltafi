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
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResults;

import java.util.function.Predicate;

public class TransformResultsAssert extends ResultAssert<TransformResultsAssert, TransformResults> {
    /**
     * Create a new TransformResultsAssert with the given result.
     *
     * @param transformResults results to validate
     * @return a new TransformResultsAssert
     */
    public static TransformResultsAssert assertThat(TransformResults transformResults) {
        return new TransformResultsAssert(transformResults);
    }

    public TransformResultsAssert(TransformResults transformResults) {
        super(transformResults, TransformResultsAssert.class);
    }

    /**
     * Verify that the transformResults list has a size equal to the given count.
     *
     * @param count expected size
     * @return this
     */
    public TransformResultsAssert hasChildrenSize(int count) {
        isNotNull();
        Assertions.assertThat(actual.getTransformResults()).hasSize(count);
        return this;
    }

    /**
     * Runs the provided match predicate against the child TransformResult at the specified index.
     *
     * @param index position of the TransformResult in the child list
     * @param childMatcher predicate used to find the matching child
     * @return this
     */
    public TransformResultsAssert hasChildResultAt(int index, Predicate<TransformResult> childMatcher) {
        isNotNull();
        if (actual.getTransformResults() == null || index >= actual.getTransformResults().size()) {
            String contentSize = actual.getTransformResults() == null ? "content list  is null" :
                    "content list has size " + actual.getTransformResults().size();
            failWithMessage("There is no content at index %s (%s)", index, contentSize);
            return myself;
        }
        TransformResult transformResult = actual.getTransformResults().get(index).getLeft();
        Assertions.assertThat(transformResult).matches(childMatcher);
        return myself;
    }
}
