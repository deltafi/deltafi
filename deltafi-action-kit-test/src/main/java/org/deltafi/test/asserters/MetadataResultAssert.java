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

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.MetadataResult;

import java.util.Map;

/**
 * Base class that provides assertions on the map of metadata in a result
 * @param <A> The class that extended this
 * @param <T> The expected result type
 */
public abstract class MetadataResultAssert<A extends AbstractAssert<A, T>, T extends MetadataResult<T>>
        extends ResultAssert<A, T> {


    protected MetadataResultAssert(T metadataResult, Class<?> selfType) {
        super(metadataResult, selfType);
    }

    /**
     * Verify that the result includes the key and value in the metadata map
     * @param key to search for
     * @param value that should be set for the key
     * @return this
     */
    public A addedMetadata(String key, String value) {
        Assertions.assertThat(actual.getMetadata()).containsEntry(key, value);
        return myself;
    }

    /**
     * Verify that the result include all the given metadata
     * @param metadata that should be included in the result
     * @return this
     */
    public A addedMetadataEquals(Map<String, String> metadata) {
        Assertions.assertThat(actual.getMetadata()).isEqualTo(metadata);
        return myself;
    }

    /**
     * Verify that result deleted the given list of keys from the metadata
     * @param keys zero or more keys that should have been deleted
     * @return this
     */
    public A deletedKeyEquals(String ... keys) {
        Assertions.assertThat(actual.getDeleteMetadataKeys()).containsExactly(keys);
        return myself;
    }
}
