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


import org.deltafi.actionkit.action.transform.TransformResult;

/**
 * Assertions for TransformResults
 */
public class TransformResultAssert extends ContentResultAssert<TransformResultAssert, TransformResult> {

    public TransformResultAssert(TransformResult transformResult) {
        super(transformResult, TransformResultAssert.class);
    }

    /**
     * Create a new TransformResultAssert with the given result
     * @param transformResult to validate
     * @return a new TransformResultAssert
     */
    public static TransformResultAssert assertThat(TransformResult transformResult) {
        return new TransformResultAssert(transformResult);
    }
}
