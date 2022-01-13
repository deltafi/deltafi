package org.deltafi.actionkit.action;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.common.metric.Metric;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Getter
@EqualsAndHashCode
public abstract class Result {
    private static final String FILES_IN = "files_in";
    private static final String FILES_COMPLETED = "files_completed";
    private static final String FILES_ERRORED = "files_errored";
    private static final String FILES_FILTERED = "files_filtered";

    protected ActionContext actionContext;

    public Result(ActionContext actionContext) {
        this.actionContext = actionContext;
    }

    public abstract ActionEventType actionEventType();

    public ActionEventInput toEvent() {
        return ActionEventInput.newBuilder()
                .did(actionContext.getDid())
                .action(actionContext.getName())
                .time(OffsetDateTime.now())
                .type(actionEventType())
                .build();
    }

    public ActionContext getActionContext() {
        return actionContext;
    }

    public Collection<Metric> getCustomMetrics() {
        return Collections.emptyList();
    }

    public Collection<Metric> getMetrics() {
        List<String> metricCounters = new ArrayList<>();

        switch (actionEventType()) {
            case ERROR:
                metricCounters.addAll(List.of(FILES_IN, FILES_ERRORED));
                break;
            case FILTER:
                metricCounters.addAll(List.of(FILES_IN, FILES_FILTERED));
                break;
            default:
                metricCounters.addAll(List.of(FILES_IN, FILES_COMPLETED));
        }

        ArrayList<Metric> metrics = new ArrayList<>();
        metricCounters.forEach(counter -> metrics.add(Metric.builder().name(counter).value(1).build()));
        metrics.addAll(getCustomMetrics());
        return metrics;
    }
}
