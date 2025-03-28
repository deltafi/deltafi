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
package org.deltafi.common.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Metric {
    private String name;
    private long value;
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();

    public Metric(String name, long value) {
        this.name = name;
        this.value = value;
        this.tags = new HashMap<>();
    }

    public Metric addTag(String key, String value) {
        if (key.contains("=") || key.contains(";")) {
            throw new IllegalArgumentException("Metric keys (" + key + "," + value + ") cannot contain = or ;");
        }

        if (value.contains("=") || value.contains(";")) {
            throw new IllegalArgumentException("Metric values (" + key + "," + value + ") cannot contain = or ;");
        }

        tags.put(key, value);

        return this;
    }

    public Metric addTags(@NotNull Map<String, String> map) {
        map.forEach(this::addTag);
        return this;
    }

    @SuppressWarnings("unused")
    public void removeTag(String key) {
        tags.remove(key);
    }

    /**
     * Generate a fully tagged metric name for this metric (STATSD format)
     * @return String for fully tagged metric name
     */
    @JsonIgnore
    public String metricName() {
        if (tags.isEmpty()) return name;
        StringBuilder taglist = new StringBuilder(name);
        tags.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    taglist.append(";");
                    taglist.append(entry.getKey()).append("=").append(entry.getValue());
                });

        return taglist.toString();
    }
}
