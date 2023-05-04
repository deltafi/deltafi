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
package org.deltafi.actionkit.action;

import java.util.Map;

public interface HasIndexedMetadata {

    /**
     * Add metadata to this DeltaFile that will be indexed and made searchable.
     * Multiple entries can be added by repeatedly calling this method.
     * @param key that will be indexed
     * @param value value for the given key
     */
    default void addIndexedMetadata(String key, String value) {
        getIndexedMetadata().put(key, value);
    }

    /**
     * Add all the metadata in the given map to this Result. These entries will be indexed and searchable.
     * @param metadata map of entries that will be added to the indexed metadata
     */
    default void addIndexedMetadata(Map<String, String> metadata) {
        getIndexedMetadata().putAll(metadata);
    }

    /**
     * Get the indexed metadata map
     * @return Map of metadata that should be indexed
     */
    Map<String, String> getIndexedMetadata();


}
