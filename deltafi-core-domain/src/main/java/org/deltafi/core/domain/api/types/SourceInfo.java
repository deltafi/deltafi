/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.api.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.core.domain.api.converters.KeyValueConverter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceInfo {
    private String filename;
    private String flow;
    private List<KeyValue> metadata = new ArrayList<>();

    @JsonIgnore
    public Map<String, String> getMetadataAsMap() {
        return KeyValueConverter.convertKeyValues(metadata);
    }

    @JsonIgnore
    public String getMetadata(String key) {
        return getMetadata(key, null);
    }

    @JsonIgnore
    public String getMetadata(String key, String defaultValue) {
        return metadata.stream().filter(k-> k.getKey().equals(key)).findFirst().map(KeyValue::getValue).orElse(defaultValue);
    }

    @JsonIgnore
    public void addMetadata(KeyValue keyValue) {
        metadata.add(keyValue);
    }

    @JsonIgnore
    public void addMetadata(List<KeyValue> keyValues) {
        if (keyValues == null) { return; }
        metadata.addAll(keyValues);
    }

    @JsonIgnore
    public void addMetadata(String key, String value) {
        metadata.add(new KeyValue(key, value));
    }

    @JsonIgnore
    public void addMetadata(@NotNull Map<String, String> map) {
        map.forEach(this::addMetadata);
    }
}
