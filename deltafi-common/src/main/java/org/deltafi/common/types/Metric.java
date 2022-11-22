/*
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

package org.deltafi.common.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.converters.KeyValueConverter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Metric {
    private String name;
    private long value;
    @Builder.Default
    private List<KeyValue> tags = new ArrayList<>();

    public
    Metric(String name, long value, @NotNull Map<String, String> tags) {
        this.name = name;
        this.value = value;
        this.tags = KeyValueConverter.fromMap(tags);
    }

    public
    Metric(String name, long value) {
        this.name = name;
        this.value = value;
        this.tags = new ArrayList<>();
    }

    @JsonIgnore
    public Map<String, String> getTagsAsMap() {
        return KeyValueConverter.convertKeyValues(tags);
    }

    public void addTag(KeyValue keyValue) {
        Optional<KeyValue> existing = tags.stream().filter(kv -> kv.getKey().equals(keyValue.getKey())).findFirst();
        if (existing.isPresent()) {
            existing.get().setValue(keyValue.getValue());
        } else {
            tags.add(keyValue);
        }
    }

    public Metric addTag(List<KeyValue> keyValues) {
        if (keyValues == null) { return this; }
        for (KeyValue keyValue : keyValues) {
            addTag(keyValue);
        }
        return this;
    }

    public Metric addTag(String key, String value) {
        addTag(new KeyValue(key, value));
        return this;
    }

    public Metric addTags(@NotNull Map<String, String> map) {
        map.forEach(this::addTag);
        return this;
    }

    public void removeTag(String key) {
        tags.removeIf(kv -> kv.getKey().equals(key));
    }

    /**
     * Generate a fully tagged metric name for this metric (STATSD format)
     * @return String for fully tagged metric name
     */
    @JsonIgnore
    public String metricName() {

        String taglist = tags.stream()
                .map(t -> t.getKey() + "=" + t.getValue())
                .sorted()
                .collect(Collectors.joining(";"));

        if (taglist.isBlank()) return name;

        return String.join( ";", name, taglist);
    }

}
