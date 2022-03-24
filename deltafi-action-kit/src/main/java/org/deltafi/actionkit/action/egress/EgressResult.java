package org.deltafi.actionkit.action.egress;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.metric.Metric;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Getter
@EqualsAndHashCode(callSuper = true)
public class EgressResult extends Result {
    private final String destination;
    private final long bytesEgressed;

    public EgressResult(@NotNull ActionContext context, String destination, long bytesEgressed) {
        super(context);

        this.destination = destination;
        this.bytesEgressed = bytesEgressed;
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.EGRESS;
    }

    @Override
    public Collection<Metric> getCustomMetrics() {
        ArrayList<Metric> metrics = new ArrayList<>();

        Map<String, String> tags = Map.of("endpoint", destination);
        metrics.add(Metric.builder().name("files_out").value(1).tags(tags).build());
        metrics.add(Metric.builder().name("bytes_out").value(bytesEgressed).tags(tags).build());

        return metrics;
    }
}
