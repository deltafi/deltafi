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
package org.deltafi.test.asserters;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.load.LoadManyResult;
import org.deltafi.actionkit.action.load.LoadResult;

import java.util.function.Predicate;

/**
 * Assertions for LoadManyResults
 */
public class LoadManyResultAssert extends ResultAssert<LoadManyResultAssert, LoadManyResult> {
    public LoadManyResultAssert(LoadManyResult loadManyResult) {
        super(loadManyResult, LoadManyResultAssert.class);
    }

    /**
     * Create a new LoadManyResultAssert with the given result
     * @param loadManyResult to validate
     * @return new LoadManyResultAssert
     */
    public static LoadManyResultAssert assertThat(LoadManyResult loadManyResult) {
        return new LoadManyResultAssert(loadManyResult);
    }

    /**
     * Verify that at least one child LoadResult in the results childLoadResult list
     * satisfies the given predicate
     * @param childMatcher predicate used to find the matching child LoadResult
     * @return this
     */
    public LoadManyResultAssert hasChildMatching(Predicate<LoadResult> childMatcher) {
        isNotNull();
        Assertions.assertThat(actual.getChildLoadResults()).anyMatch(childMatcher);
        return this;
    }

    /**
     * Verify that the list of child LoadResults has the given size
     * @param size expected child LoadResult list size
     * @return this
     */
    public LoadManyResultAssert hasChildrenSize(int size) {
        isNotNull();
        Assertions.assertThat(actual.getChildLoadResults()).hasSize(size);
        return this;
    }
}
