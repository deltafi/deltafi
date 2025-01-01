/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.deltafi.actionkit.exception.MissingMetadataException;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Action input that may include metadata
 */
@Data
@SuperBuilder
public abstract class MetadataInput {
    @Builder.Default
    protected Map<String, String> metadata = new HashMap<>();

    /**
     * Returns the value of the last action's metadata for the given key.
     * @param key the key for the metadata.
     * @return the value of the metadata for the given key.
     * @throws MissingMetadataException if the key is not found in the metadata map.
     */
    public String metadata(@NotNull String key) {
        if (!metadata.containsKey(key)) {
            throw new MissingMetadataException(key);
        }
        return metadata.get(key);
    }

    /**
     * Returns the value of the last action's metadata for the given key or a default value if the key is not found.
     * @param key the key for the metadata.
     * @param defaultValue the default value to return if the key is not found.
     * @return the value of the metadata for the given key or the default value if the key is not found.
     */
    public String metadata(@NotNull String key, @NotNull String defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }
}
