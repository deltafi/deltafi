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

public abstract class AnnotationResultAssert<A extends AbstractAssert<A, T>, T extends AnnotationsResult<T>>
        extends ResultAssert<A, T> {

    protected AnnotationResultAssert(T metadataResult, Class<?> selfType) {
        super(metadataResult, selfType);
    }

    public A addedAnnotation(String key, String value) {
        isNotNull();
        Assertions.assertThat(actual.getAnnotations()).containsEntry(key, value);
        return myself;
    }

    public A addedAnnotations(Map<String, String> metadata) {
        isNotNull();
        Assertions.assertThat(actual.getAnnotations()).containsAllEntriesOf(metadata);
        return myself;
    }

    public A annotationsIsEmpty() {
        isNotNull();
        Assertions.assertThat(actual.getAnnotations()).isEmpty();
        return myself;
    }

}
