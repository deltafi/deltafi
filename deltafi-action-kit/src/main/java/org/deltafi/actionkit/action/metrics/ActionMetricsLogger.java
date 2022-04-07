package org.deltafi.actionkit.action.metrics;

import org.deltafi.actionkit.action.Result;
import org.deltafi.common.metric.Metric;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.actionkit.action.ActionType;

import java.util.Collection;
import java.util.HashMap;

public class ActionMetricsLogger {

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
