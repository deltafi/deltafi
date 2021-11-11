package org.deltafi.actionkit.action.metrics;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.metric.Metric;
import org.deltafi.core.domain.generated.types.ActionEventType;

import java.util.Collection;

public interface ActionMetricsGenerator {
    ActionEventType getActionEventType();

    Collection<Metric> generateMetrics(Result result);
}
