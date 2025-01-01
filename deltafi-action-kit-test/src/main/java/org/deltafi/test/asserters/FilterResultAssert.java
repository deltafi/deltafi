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
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.filter.FilterResult;

import java.util.regex.Pattern;

/**
 * Assertions for FilterResults
 */
public class FilterResultAssert extends AnnotationResultAssert<FilterResultAssert, FilterResult> {
    /**
     * Create a new FilterResultAssert with the given result, asserting that the result is not null and an instance
     * of FilterResult.
     * @param result to validate
     * @return a new FilterResultAssert
     */
    public static FilterResultAssert assertThat(ResultType result) {
        return assertThat(result, "Is non-null FilterResult");
    }

    /**
     * Create a new FilterResultAssert with the given result, asserting that the result is not null and an instance
     * of FilterResult.
     * @param result to validate
     * @param description a description to include with the not null and instance of assertions
     * @return a new FilterResultAssert
     */
    public static FilterResultAssert assertThat(ResultType result, String description) {
        ResultAssertions.assertNonNullResult(result, FilterResult.class, description);
        return new FilterResultAssert((FilterResult) result);
    }

    private FilterResultAssert(FilterResult filterResult) {
        super(filterResult, FilterResultAssert.class);
    }

    /**
     * Verify the filter cause is equal to the given cause
     * @param exactMatch expected filter cause
     * @return this
     */
    public FilterResultAssert hasCause(String exactMatch) {
        return hasCause(exactMatch, "Has cause");
    }

    /**
     * Verify the filter cause is equal to the given cause
     * @param exactMatch expected filter cause
     * @param description a description to include with the assertion
     * @return this
     */
    public FilterResultAssert hasCause(String exactMatch, String description) {
        Assertions.assertThat(actual.getFilteredCause()).describedAs(description).isEqualTo(exactMatch);
        return this;
    }

    /**
     * Verify the filter cause matches the given regex pattern
     * @param regexPattern to match against the filter cause
     * @return this
     */
    public FilterResultAssert hasCauseLike(String regexPattern) {
        return hasCauseLike(regexPattern, "Has cause like");
    }

    /**
     * Verify the filter cause matches the given regex pattern
     * @param regexPattern to match against the filter cause
     * @param description a description to include with the assertion
     * @return this
     */
    public FilterResultAssert hasCauseLike(String regexPattern, String description) {
        Pattern pattern = Pattern.compile(regexPattern);
        Assertions.assertThat(actual.getFilteredCause()).describedAs(description).matches(pattern);
        return this;
    }

    /**
     * Verify the filter context is equal to the given context
     * @param exactMatch expected filter context
     * @return this
     */
    public FilterResultAssert hasContext(String exactMatch) {
        return hasContext(exactMatch, "Has context");
    }

    /**
     * Verify the filter context is equal to the given context
     * @param exactMatch expected filter context
     * @param description a description to include with the assertion
     * @return this
     */
    public FilterResultAssert hasContext(String exactMatch, String description) {
        Assertions.assertThat(actual.getFilteredContext()).describedAs(description).isEqualTo(exactMatch);
        return this;
    }

    /**
     * Verify the filter context matches the given regex pattern
     * @param regexPattern to match against the filter context
     * @return this
     */
    public FilterResultAssert hasContextLike(String regexPattern) {
        return hasContextLike(regexPattern, "Has context like");
    }

    /**
     * Verify the filter context matches the given regex pattern
     * @param regexPattern to match against the filter context
     * @param description a description to include with the assertion
     * @return this
     */
    public FilterResultAssert hasContextLike(String regexPattern, String description) {
        Pattern pattern = Pattern.compile(regexPattern);
        Assertions.assertThat(actual.getFilteredContext()).describedAs(description).matches(pattern);
        return this;
    }
}
