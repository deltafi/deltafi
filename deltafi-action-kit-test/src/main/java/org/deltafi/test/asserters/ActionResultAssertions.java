/**
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
import org.deltafi.actionkit.action.load.ReinjectResult;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.validate.ValidateResult;

import static org.assertj.core.api.Assertions.assertThat;

public class ActionResultAssertions {

    private ActionResultAssertions() {
    }

    public static TransformResultAssert assertTransformResult(ResultType resultType) {
        return new TransformResultAssert(cast(resultType, TransformResult.class));
    }

    public static ReinjectResultAssert assertReinjectResult(ResultType resultType) {
        return new ReinjectResultAssert(cast(resultType, ReinjectResult.class));
    }

    public static LoadResultAssert assertLoadResult(ResultType resultType) {
        return new LoadResultAssert(cast(resultType, LoadResult.class));
    }

    public static LoadManyResultAssert assertLoadManyResult(ResultType resultType) {
        return new LoadManyResultAssert(cast(resultType, LoadManyResult.class));
    }

    public static ErrorResultAssert assertErrorResult(ResultType resultType) {
        return new ErrorResultAssert(cast(resultType, ErrorResult.class));
    }

    public static FilterResultAssert assertFilterResult(ResultType resultType) {
        return new FilterResultAssert(cast(resultType, FilterResult.class));
    }

    public static DomainResultAssert assertDomainResult(ResultType resultType) {
        return new DomainResultAssert(cast(resultType, DomainResult.class));
    }

    public static EnrichResultAssert assertEnrichResult(ResultType resultType) {
        return new EnrichResultAssert(cast(resultType, EnrichResult.class));
    }

    public static FormatResultAssert assertFormatResult(ResultType resultType) {
        return new FormatResultAssert(cast(resultType, FormatResult.class));
    }

    public static FormatManyResultAssert assertFormatManyResult(ResultType resultType) {
        return new FormatManyResultAssert(cast(resultType, FormatManyResult.class));
    }

    public static ValidateResultAssert assertValidateResult(ResultType resultType) {
        return new ValidateResultAssert(cast(resultType, ValidateResult.class));
    }

    public static EgressResultAssert assertEgressResult(ResultType resultType) {
        return new EgressResultAssert(cast(resultType, EgressResult.class));
    }

    private static <T extends ResultType, R extends Result<R>> R cast(T rawResult, Class<R> expectedResultType) {
        assertThat(rawResult).isInstanceOf(expectedResultType);
        return expectedResultType.cast(rawResult);
    }
}
