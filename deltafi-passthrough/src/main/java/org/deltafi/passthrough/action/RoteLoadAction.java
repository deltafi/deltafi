package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.actionkit.service.ContentService;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.passthrough.param.RoteLoadParameters;

import javax.inject.Inject;

@SuppressWarnings("unused")
@Slf4j
public class RoteLoadAction extends Action<RoteLoadParameters> {

    @Inject
    ContentService contentService;

    public Result execute(DeltaFile deltafile, RoteLoadParameters params) {
        log.trace(params.getName() + " loading (" + deltafile.getDid() + ")");

        LoadResult result = new LoadResult(params.getName(), deltafile.getDid());

        params.getDomains().forEach(result::addDomain);

        generateMetrics(deltafile);

        return result;
    }

    private static final MetricLogger metricLogger = new MetricLogger();
    static final String LOG_SOURCE = "load";
    static final String FILES_PROCESSED = "files_processed";

    void generateMetrics(DeltaFile deltafile) {
        Tag[] tags = {
                new Tag("did", deltafile.getDid()),
                new Tag("flow", deltafile.getSourceInfo().getFlow()),
                new Tag("action", getClass().getSimpleName())
        };

        metricLogger.logMetric(LOG_SOURCE, MetricType.COUNTER, FILES_PROCESSED, 1, tags);
    }

    @Override
    public Class<RoteLoadParameters> getParamType() {
        return RoteLoadParameters.class;
    }
}