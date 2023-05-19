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

public interface HasAnnotations {

    /**
     * Add annotation to this DeltaFile that will be made searchable.
     * Multiple entries can be added by repeatedly calling this method.
     * @param key that will be annotated
     * @param value value for the given key
     */
    default void addAnnotation(String key, String value) {
        getAnnotations().put(key, value);
    }

    /**
     * Add all the annotations in the given map to this Result. These entries will be searchable.
     * @param metadata map of entries that will be added to the annotations
     */
    default void addAnnotation(Map<String, String> metadata) {
        getAnnotations().putAll(metadata);
    }

    /**
     * Get the annotations map
     * @return Map of annotations
     */
    Map<String, String> getAnnotations();


}
