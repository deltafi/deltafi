package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.format.FormatAction;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.core.domain.api.types.DeltaFile;

@SuppressWarnings("unused")
@Slf4j
public class RoteFormatAction extends FormatAction<ActionParameters> {

    public Result execute(DeltaFile deltafile, ActionParameters params) {
        log.trace(params.getName() + " formatting (" + deltafile.getDid() + ")");

        FormatResult result = new FormatResult(params.getName(), deltafile.getDid(), deltafile.getSourceInfo().getFilename());

        deltafile.getSourceInfo().getMetadata().forEach(kv -> result.addMetadata("sourceInfo." + kv.getKey(), kv.getValue()));
        params.getStaticMetadata().forEach(result::addMetadata);

        result.setObjectReference(deltafile.getProtocolStack().get(0).getObjectReference());
        generateMetrics(deltafile, params.getName());

        return result;
    }

    private static final MetricLogger metricLogger = new MetricLogger();
    static final String LOG_SOURCE = "format";
    static final String FILES_PROCESSED = "files_processed";

    @Override
    public void generateMetrics(DeltaFile deltafile, String name) {
        Tag[] tags = {
                new Tag("did", deltafile.getDid()),
                new Tag("flow", deltafile.getSourceInfo().getFlow()),
                new Tag("action", getClass().getSimpleName())
        };

        metricLogger.logMetric(LOG_SOURCE, MetricType.COUNTER, FILES_PROCESSED, 1, tags);
    }

    @Override
    public Class<ActionParameters> getParamType() {
        return ActionParameters.class;
    }
}