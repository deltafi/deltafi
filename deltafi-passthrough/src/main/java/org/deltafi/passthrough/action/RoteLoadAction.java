package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.action.Result;
import org.deltafi.action.load.LoadAction;
import org.deltafi.action.load.LoadResult;
import org.deltafi.config.DeltafiConfig;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.service.ContentService;
import org.deltafi.types.DeltaFile;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
@Slf4j
public class RoteLoadAction extends LoadAction {

    final ContentService contentService;
    final List<String> domains = new ArrayList<>();

    @SuppressWarnings("unused")
    public RoteLoadAction() {
        super();
        contentService = ContentService.instance();
    }

    public void init(DeltafiConfig.ActionSpec spec) {
        super.init(spec);
        if (spec.parameters.get("domain") != null) domains.add(spec.parameters.get("domain").toString());
        // Add parameter processing here...
    }

    public Result execute(DeltaFile deltafile) {
        log.trace(name + " loading (" + deltafile.getDid() + ")");

        LoadResult result = new LoadResult(this, deltafile.getDid());

        domains.forEach(result::addDomain);

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
}