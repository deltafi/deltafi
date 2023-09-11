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

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.AnnotationsResult;

import java.util.Map;

/**
 * Base class that provides assertions for annotations
 * @param <A> The class that is extending this
 * @param <T> The expected ResultType class
 */
public abstract class AnnotationResultAssert<A extends AbstractAssert<A, T>, T extends AnnotationsResult<T>>
        extends ResultAssert<A, T> {

    protected AnnotationResultAssert(T metadataResult, Class<?> selfType) {
        super(metadataResult, selfType);
    }

    /**
     * Verify that the result includes the key and value in the annotation map
     * @param key to search for
     * @param value that should be set for the key
     * @return this
     */
    public A addedAnnotation(String key, String value) {
        isNotNull();
        Assertions.assertThat(actual.getAnnotations()).containsEntry(key, value);
        return myself;
    }

    /**
     * Verify that the result include all the given annotations
     * @param annotations that should be included in the result
     * @return this
     */
    public A addedAnnotations(Map<String, String> annotations) {
        isNotNull();
        Assertions.assertThat(actual.getAnnotations()).containsAllEntriesOf(annotations);
        return myself;
    }

    /**
     * Verify that no annotations were added
     * @return this
     */
    public A annotationsIsEmpty() {
        isNotNull();
        Assertions.assertThat(actual.getAnnotations()).isEmpty();
        return myself;
    }

}
