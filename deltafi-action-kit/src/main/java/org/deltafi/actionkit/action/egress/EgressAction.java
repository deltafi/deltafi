package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.metric.Metric;
import org.deltafi.core.domain.generated.types.ActionEventType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public abstract class EgressAction<P extends EgressActionParameters> extends Action<P> {
    public EgressAction(Class<P> actionParametersClass) {
        super(actionParametersClass, ActionEventType.EGRESS);
    }

    @Override
    public Collection<Metric> generateMetrics(Result result) {
        ArrayList<Metric> metrics = new ArrayList<>();

        Map<String, String> tags = Map.of("endpoint", ((EgressResult) result).getDestination());
        metrics.add(Metric.builder().name("files_out").value(1).tags(tags).build());
        metrics.add(Metric.builder().name("bytes_out").value(((EgressResult) result).getBytesEgressed()).tags(tags).build());

        return metrics;
    }
}
