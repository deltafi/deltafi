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
package org.deltafi.actionkit.action.validate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.deltafi.actionkit.exception.MissingSourceMetadataException;
import org.deltafi.common.types.FormattedData;

import java.util.Map;

@AllArgsConstructor
@Builder
@Data
public class ValidateInput {
    String sourceFilename;
    String ingressFlow;
    Map<String, String> sourceMetadata;
    FormattedData formattedData;

    /**
     * Returns the value of the source metadata for the given key.
     * @param key the key for the metadata.
     * @return the value of the metadata for the given key.
     * @throws MissingSourceMetadataException if the key is not found in the source metadata map.
     */
    public String sourceMetadata(String key) {
        if (sourceMetadata.containsKey(key)) {
            return sourceMetadata.get(key);
        } else {
            throw new MissingSourceMetadataException(key);
        }
    }

    /**
     * Returns the value of the source metadata for the given key or a default value if the key is not found.
     * @param key the key for the metadata.
     * @param defaultValue the default value to return if the key is not found.
     * @return the value of the metadata for the given key or the default value if the key is not found.
     */
    public String sourceMetadata(String key, String defaultValue) {
        return sourceMetadata.getOrDefault(key, defaultValue);
    }
}
