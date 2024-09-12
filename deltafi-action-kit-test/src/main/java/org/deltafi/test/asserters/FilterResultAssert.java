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
import org.deltafi.actionkit.action.filter.FilterResult;

import java.util.regex.Pattern;

/**
 * Assertions for FilterResults
 */
public class FilterResultAssert extends AnnotationResultAssert<FilterResultAssert, FilterResult> {

    public FilterResultAssert(FilterResult filterResult) {
        super(filterResult, FilterResultAssert.class);
    }

    /**
     * Create a new FilterResultAssert with the given result
     *
     * @param filterResult to validate
     * @return a new FilterResultAssert
     */
    public static FilterResultAssert assertThat(FilterResult filterResult) {
        return new FilterResultAssert(filterResult);
    }

    /**
     * Verify the filter cause is equal to the given cause
     *
     * @param exactMatch expected filter cause
     * @return this
     */
    public FilterResultAssert hasCause(String exactMatch) {
        Assertions.assertThat(actual.getFilteredCause()).isEqualTo(exactMatch);
        return this;
    }

    /**
     * Verify the filter context is equal to the given context
     *
     * @param exactMatch expected filter context
     * @return this
     */
    public FilterResultAssert hasContext(String exactMatch) {
        Assertions.assertThat(actual.getFilteredContext()).isEqualTo(exactMatch);
        return this;
    }

    /**
     * Verify the filter cause matches the given regex pattern
     *
     * @param regexPattern to match against the filter cause
     * @return this
     */
    public FilterResultAssert hasCauseLike(String regexPattern) {
        Pattern pattern = Pattern.compile(regexPattern);
        Assertions.assertThat(actual.getFilteredCause()).matches(pattern);
        return this;
    }

    /**
     * Verify the filter context matches the given regex pattern
     *
     * @param regexPattern to match against the filter context
     * @return this
     */
    public FilterResultAssert hasContextLike(String regexPattern) {
        Pattern pattern = Pattern.compile(regexPattern);
        Assertions.assertThat(actual.getFilteredContext()).matches(pattern);
        return this;
    }

}
