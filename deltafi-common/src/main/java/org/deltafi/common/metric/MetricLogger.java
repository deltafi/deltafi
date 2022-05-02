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
package org.deltafi.common.metric;

import io.quarkiverse.loggingjson.providers.KeyValueStructuredArgument;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class MetricLogger {
    /**
     * Logs a counter metric for a source, delta file id, and flow. Delta file id and flow are added as the only tags.
     * 
     * @param source the source
     * @param did the delta file id
     * @param flow the flow
     * @param name the name
     * @param value the value
     */
    public static void logMetric(String source, String did, String flow, String name, long value) {
        logMetric(source, did, flow, name, value, Map.of());
    }

    /**
     * Logs a counter metric for a source, delta file id, and flow. Delta file id and flow are added as tags along with
     * the provided additional tags.
     *
     * @param source the source
     * @param did the delta file id
     * @param flow the flow
     * @param name the name
     * @param value the value
     * @param additionalTags additional tags (key/value pairs) to add to the log entry
     */
    public static void logMetric(String source, String did, String flow, String name, long value,
                                 Map<String, String> additionalTags) {
        Map<String, String> tags = new HashMap<>();
        tags.put("did", did);
        tags.put("flow", flow);
        tags.putAll(additionalTags);

        logMetric(source, name, value, tags);
    }

    /**
     * Logs a counter metric for a source.
     *
     * @param source the source
     * @param name the name
     * @param value the value
     * @param tags additional tags (key/value pairs) to add to the log entry
     */
    public static void logMetric(String source, String name, long value, Map<String, String> tags) {
        logMetric(source, MetricType.COUNTER, name, value, tags);
    }

    /**
     * Logs a metric for a source.
     *
     * @param source the source
     * @param type the type
     * @param name the name
     * @param value the value
     * @param tags additional tags (key/value pairs) to add to the log entry
     */
    public static void logMetric(String source, MetricType type, String name, long value, Map<String, String> tags) {
        Metric metric = Metric.builder()
                .source(source)
                .name(name)
                .value(value)
                .type(type)
                .timestamp(new Date())
                .tags(tags)
                .build();

        log.info("{}", KeyValueStructuredArgument.kv("metric", metric));
    }
}
