/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResults;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assertions that verify the ResultType is the correct class and delegates
 * to the appropriate ResultAssert.
 */
public class ActionResultAssertions {

    private ActionResultAssertions() {
    }

    /**
     * Return a new TransformResultAssert if the given ResultType is an instance of a TransformResult otherwise fail
     * @param resultType result to check
     * @return a new TransformResultAssert if it is the correct type otherwise fail
     */
    public static TransformResultAssert assertTransformResult(ResultType resultType) {
        return TransformResultAssert.assertThat(cast(resultType, TransformResult.class));
    }

    /**
     * Return a new TransformResultsAssert if the given ResultType is an instance of a TransformResults otherwise fail
     * @param resultType result to check
     * @return a new TransformResultsAssert if it is the correct type otherwise fail
     */
    public static TransformResultsAssert assertTransformResults(ResultType resultType) {
        return TransformResultsAssert.assertThat(cast(resultType, TransformResults.class));
    }

    /**
     * Return a new ErrorResultAssert if the given ResultType is an instance of an ErrorResult otherwise fail
     * @param resultType result to check
     * @return a new ErrorResultAssert if it is the correct type otherwise fail
     */
    public static ErrorResultAssert assertErrorResult(ResultType resultType) {
        return ErrorResultAssert.assertThat(cast(resultType, ErrorResult.class));
    }

    /**
     * Return a new FilterResultAssert if the given ResultType is an instance of a FilterResult otherwise fail
     * @param resultType result to check
     * @return a new FilterResultAssert if it is the correct type otherwise fail
     */
    public static FilterResultAssert assertFilterResult(ResultType resultType) {
        return FilterResultAssert.assertThat(cast(resultType, FilterResult.class));
    }

    /**
     * Return a new EgressResultAssert if the given ResultType is an instance of an EgressResult otherwise fail
     * @param resultType result to check
     * @return a new EgressResultAssert if it is the correct type otherwise fail
     */
    public static EgressResultAssert assertEgressResult(ResultType resultType) {
        return EgressResultAssert.assertThat(cast(resultType, EgressResult.class));
    }

    private static <T extends ResultType, R extends Result<R>> R cast(T rawResult, Class<R> expectedResultType) {
        assertThat(rawResult).isInstanceOf(expectedResultType);
        return expectedResultType.cast(rawResult);
    }
}
