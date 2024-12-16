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

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.transform.TransformResult;

/**
 * Assertions for TransformResults
 */
public class TransformResultAssert extends ContentResultAssert<TransformResultAssert, TransformResult> {
    /**
     * Create a new TransformResultAssert with the given result, asserting that the result is not null and an instance
     * of TransformResult.
     * @param result to validate
     * @return a new TransformResultAssert
     */
    public static TransformResultAssert assertThat(ResultType result) {
        return assertThat(result, "Is non-null TransformResult");
    }

    /**
     * Create a new TransformResultAssert with the given result, asserting that the result is not null and an instance
     * of TransformResult.
     * @param result to validate
     * @param description a description to include with the not null and instance of assertions
     * @return a new TransformResultAssert
     */
    public static TransformResultAssert assertThat(ResultType result, String description) {
        ResultAssertions.assertNonNullResult(result, TransformResult.class, description);
        return new TransformResultAssert((TransformResult) result);
    }

    private TransformResultAssert(TransformResult transformResult) {
        super(transformResult, TransformResultAssert.class);
    }
}
