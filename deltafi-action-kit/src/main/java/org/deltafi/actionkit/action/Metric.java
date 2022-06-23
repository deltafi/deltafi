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
package org.deltafi.actionkit.action;

import lombok.*;

import java.util.Map;

/**
 * Metric object to provide a simple monotonically increasing counter metric
 */
@Data
@Builder(builderMethodName = "hiddenBuilder")
@RequiredArgsConstructor
@AllArgsConstructor
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
    private Map<String, String> tags;

    public static class MetricBuilder {}

    public static MetricBuilder builder(String name, long value) {
        return hiddenBuilder().name(name).value(value);
    }

}
