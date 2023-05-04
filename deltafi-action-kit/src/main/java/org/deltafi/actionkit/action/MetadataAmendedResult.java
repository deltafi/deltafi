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
package org.deltafi.actionkit.action;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.deltafi.common.types.ActionContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Specialization of the Result base class that allows metadata and content to be collected in the result.
 * <p>
 * This class is extended for Load and Transform results
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public abstract class MetadataAmendedResult extends Result<MetadataAmendedResult> {
    protected Map<String, String> metadata = new HashMap<>();

    /**
     * @param context Action context
     */
    public MetadataAmendedResult(ActionContext context) {
        super(context);
    }

    /**
     * Add metadata by key and value Strings
     * @param key Metadata key to add
     * @param value Metadata value to add
     */
    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }

    /**
     * Add metadata by map
     * @param map Key-value pairs to add to metadata
     */
    @SuppressWarnings("unused")
    public void addMetadata(Map<String, String> map) {
        if (map != null) {
            metadata.putAll(map);
        }
    }

    /**
     * Add metadata by key/value map, prefixing each key with a fixed string
     * @param map String pairs to add to metadata
     * @param prefix String to prepend to each key before adding to metadata
     */
    public void addMetadata(Map<String, String> map, String prefix) {
        if (map != null) {
            final String usePrefix = prefix != null ? prefix : "";
            map.forEach((key, value) -> metadata.put(usePrefix + key, value));
        }
    }
}
