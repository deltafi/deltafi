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
package org.deltafi.actionkit.action.metrics;

import org.deltafi.actionkit.action.Result;
import org.deltafi.common.metric.Metric;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.actionkit.action.ActionType;

import java.util.Collection;
import java.util.HashMap;

/**
 * Utility class for logging action metrics
 */
public class ActionMetricsLogger {

    /**
     * Log action metrics to the {@link MetricLogger}
     * @param actionType Type of action for the metric being logged
     * @param result Result object containing the metrics to be logged
     */
    public static void logMetrics(ActionType actionType, Result result) {
        Collection<Metric> metrics = result.getMetrics();
        for (Metric metric : metrics) {
            HashMap<String, String> tags = new HashMap<>();
            tags.put("action", result.getContext().getName());

            if (metric.getTags() != null) {
                tags.putAll(metric.getTags());
            }

            MetricLogger.logMetric(actionType.name().toLowerCase(), result.getContext().getDid(),
                    result.getContext().getIngressFlow(), metric.getName(), metric.getValue(), tags);
        }
    }
}
