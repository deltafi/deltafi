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

import org.deltafi.actionkit.action.ReinjectResult;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.domain.DomainResult;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.enrich.EnrichResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.action.format.FormatManyResult;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.load.LoadManyResult;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.validate.ValidateResult;

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
     * Return a new ReinjectResultAssert if the given ResultType is an instance of a ReinjectResult otherwise fail
     * @param resultType result to check
     * @return a new ReinjectResultAssert if it is the correct type otherwise fail
     */
    public static ReinjectResultAssert assertReinjectResult(ResultType resultType) {
        return ReinjectResultAssert.assertThat(cast(resultType, ReinjectResult.class));
    }

    /**
     * Return a new LoadResultAssert if the given ResultType is an instance of a LoadResult otherwise fail
     * @param resultType result to check
     * @return a new LoadResultAssert if it is the correct type otherwise fail
     */
    public static LoadResultAssert assertLoadResult(ResultType resultType) {
        return LoadResultAssert.assertThat(cast(resultType, LoadResult.class));
    }

    /**
     * Return a new LoadManyResultAssert if the given ResultType is an instance of a LoadManyResult otherwise fail
     * @param resultType result to check
     * @return a new LoadManyResultAssert if it is the correct type otherwise fail
     */
    public static LoadManyResultAssert assertLoadManyResult(ResultType resultType) {
        return LoadManyResultAssert.assertThat(cast(resultType, LoadManyResult.class));
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
     * Return a new DomainResultAssert if the given ResultType is an instance of a DomainResult otherwise fail
     * @param resultType result to check
     * @return a new DomainResultAssert if it is the correct type otherwise fail
     */
    public static DomainResultAssert assertDomainResult(ResultType resultType) {
        return DomainResultAssert.assertThat(cast(resultType, DomainResult.class));
    }

    /**
     * Return a new EnrichResultAssert if the given ResultType is an instance of an EnrichResult otherwise fail
     * @param resultType result to check
     * @return a new EnrichResultAssert if it is the correct type otherwise fail
     */
    public static EnrichResultAssert assertEnrichResult(ResultType resultType) {
        return EnrichResultAssert.assertThat(cast(resultType, EnrichResult.class));
    }

    /**
     * Return a new FormatResultAssert if the given ResultType is an instance of a FormatResult otherwise fail
     * @param resultType result to check
     * @return a new FormatResultAssert if it is the correct type otherwise fail
     */
    public static FormatResultAssert assertFormatResult(ResultType resultType) {
        return FormatResultAssert.assertThat(cast(resultType, FormatResult.class));
    }

    /**
     * Return a new FormatManyResultAssert if the given ResultType is an instance of a FormatManyResult otherwise fail
     * @param resultType result to check
     * @return a new FormatManyResultAssert if it is the correct type otherwise fail
     */
    public static FormatManyResultAssert assertFormatManyResult(ResultType resultType) {
        return FormatManyResultAssert.assertThat(cast(resultType, FormatManyResult.class));
    }

    /**
     * Return a new ValidateResultAssert if the given ResultType is an instance of a ValidateResult otherwise fail
     * @param resultType result to check
     * @return a new ValidateResultAssert if it is the correct type otherwise fail
     */
    public static ValidateResultAssert assertValidateResult(ResultType resultType) {
        return ValidateResultAssert.assertThat(cast(resultType, ValidateResult.class));
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
