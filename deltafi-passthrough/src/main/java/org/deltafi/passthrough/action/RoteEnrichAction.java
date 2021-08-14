package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.SimpleAction;
import org.deltafi.actionkit.action.enrich.EnrichResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.dgs.api.types.DeltaFile;

@SuppressWarnings("unused")
@Slf4j
public class RoteEnrichAction extends SimpleAction {

    private static final MetricLogger metricLogger = new MetricLogger();

    static final String LOG_SOURCE = "enrich";
    static final String FILES_PROCESSED = "files_processed";

    public Result execute(DeltaFile deltafile, ActionParameters params) {
        log.trace(params.getName() + " enrich (" + deltafile.getDid() + ")");
        generateMetrics(deltafile);
        return new EnrichResult(params.getName(), deltafile.getDid());
    }

    void generateMetrics(DeltaFile deltafile) {
        Tag[] tags = {
                new Tag("did", deltafile.getDid()),
                new Tag("flow", deltafile.getSourceInfo().getFlow()),
                new Tag("action", getClass().getSimpleName())
        };

        metricLogger.logMetric(LOG_SOURCE, MetricType.COUNTER, FILES_PROCESSED, 1, tags);
    }
}