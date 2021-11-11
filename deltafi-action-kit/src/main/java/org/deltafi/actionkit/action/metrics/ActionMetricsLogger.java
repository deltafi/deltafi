package org.deltafi.actionkit.action.metrics;

import lombok.RequiredArgsConstructor;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.metric.Metric;
import org.deltafi.common.metric.MetricLogger;

import java.util.Collection;
import java.util.HashMap;

@RequiredArgsConstructor
public class ActionMetricsLogger {
    private final ActionMetricsGenerator actionMetricsGenerator;

    public void logMetrics(Result result) {
        Collection<Metric> metrics = actionMetricsGenerator.generateMetrics(result);

        for (Metric metric : metrics) {
            HashMap<String, String> tags = new HashMap<>();
            tags.put("action", result.getParams().getName());

            if (metric.getTags() != null) {
                tags.putAll(metric.getTags());
            }

            MetricLogger.logMetric(actionMetricsGenerator.getActionEventType().name().toLowerCase(), result.getDeltaFile().getDid(),
                    result.getDeltaFile().getSourceInfo().getFlow(), metric.getName(), metric.getValue(), tags);
        }
    }
}
