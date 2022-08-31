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
package org.deltafi.common.metrics;

import lombok.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Metric object to provide a simple monotonically increasing counter metric
 */
@Data
@RequiredArgsConstructor
public class Metric {
    /**
     * Name of metric
     */
    private final String name;
    /**
     * Positive value to increment the metric
     */
    private final long value;
    /**
     * Key value pair tags for the metric to provide additional context
     */
    private final Map<String, String> tags = new HashMap<>();

    /**
     * Generate a fully tagged metric name for this metric
     * @return String for fully tagged metric name
     */
    public String metricName() {

        String taglist = tags.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(t -> t.getKey() + "=" + t.getValue())
                .collect(Collectors.joining(";"));

        if (taglist.isBlank()) return name;

        return String.join( ";", name, taglist);
    }

    public Metric addTags(Map<String, String> tags) {
        this.tags.putAll(tags);
        return this;
    }

    public Metric addTag(String key, String value) {
        tags.put(key, value);
        return this;
    }

}
