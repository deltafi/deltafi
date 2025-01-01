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

import org.deltafi.actionkit.action.ResultType;

/**
 * Assertions that verify the ResultType is the correct class and delegates to the appropriate ResultAssert.
 * @deprecated Use the appropriate ResultAssert subclass.
 */
@Deprecated
public class ActionResultAssertions {

    private ActionResultAssertions() {
    }

    /**
     * Return a new TransformResultAssert if the given ResultType is an instance of a TransformResult otherwise fail
     * @param result result to check
     * @return a new TransformResultAssert if it is the correct type otherwise fail
     * @deprecated Use {@link TransformResultAssert#assertThat(ResultType)}
     */
    @Deprecated
    public static TransformResultAssert assertTransformResult(ResultType result) {
        return TransformResultAssert.assertThat(result);
    }

    /**
     * Return a new TransformResultsAssert if the given ResultType is an instance of a TransformResults otherwise fail
     * @param result result to check
     * @return a new TransformResultsAssert if it is the correct type otherwise fail
     * @deprecated Use {@link TransformResultsAssert#assertThat(ResultType)}
     */
    @Deprecated
    public static TransformResultsAssert assertTransformResults(ResultType result) {
        return TransformResultsAssert.assertThat(result);
    }

    /**
     * Return a new ErrorResultAssert if the given ResultType is an instance of an ErrorResult otherwise fail
     * @param result result to check
     * @return a new ErrorResultAssert if it is the correct type otherwise fail
     * @deprecated Use {@link ErrorResultAssert#assertThat(ResultType)}
     */
    @Deprecated
    public static ErrorResultAssert assertErrorResult(ResultType result) {
        return ErrorResultAssert.assertThat(result);
    }

    /**
     * Return a new FilterResultAssert if the given ResultType is an instance of a FilterResult otherwise fail
     * @param result result to check
     * @return a new FilterResultAssert if it is the correct type otherwise fail
     * @deprecated Use {@link FilterResultAssert#assertThat(ResultType)}
     */
    @Deprecated
    public static FilterResultAssert assertFilterResult(ResultType result) {
        return FilterResultAssert.assertThat(result);
    }

    /**
     * Return a new EgressResultAssert if the given ResultType is an instance of an EgressResult otherwise fail
     * @param result result to check
     * @return a new EgressResultAssert if it is the correct type otherwise fail
     * @deprecated Use {@link EgressResultAssert#assertThat(ResultType)}
     */
    @Deprecated
    public static EgressResultAssert assertEgressResult(ResultType result) {
        return EgressResultAssert.assertThat(result);
    }
}
