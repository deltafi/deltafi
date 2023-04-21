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
package org.deltafi.common.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceInfo {
    private String filename;
    private String flow;
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    private ProcessingType processingType;

    public SourceInfo(String filename, String flow, Map<String, String> metadata) {
        this.filename = filename;
        this.flow = flow;
        this.metadata = metadata;
        this.processingType = ProcessingType.NORMALIZATION;
    }

    public ProcessingType getProcessingType() {
        if (processingType == null) {
            processingType = ProcessingType.NORMALIZATION;
        }

        return processingType;
    }

    public void setProcessingType(ProcessingType processingType) {
        this.processingType = Objects.requireNonNullElse(processingType, ProcessingType.NORMALIZATION);
    }

    @JsonIgnore
    public boolean containsKey(String key) {
        return metadata.containsKey(key);
    }

    @JsonIgnore
    public String getMetadata(String key) {
        return metadata.get(key);
    }

    @JsonIgnore
    public String getMetadata(String key, String defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }

    public void addMetadata(Map<String, String> map) {
        if (map != null) {
            metadata.putAll(map);
        }
    }

    public void removeMetadata(String key) {
        metadata.remove(key);
    }
}
