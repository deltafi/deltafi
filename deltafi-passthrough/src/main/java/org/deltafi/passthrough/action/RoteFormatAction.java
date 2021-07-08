package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.format.FormatAction;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.config.DeltafiConfig;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.actionkit.service.ContentService;
import org.deltafi.actionkit.types.DeltaFile;

@SuppressWarnings("unused")
@Slf4j
public class RoteFormatAction extends FormatAction {

    final ContentService contentService;

    public RoteFormatAction() {
        super();
        contentService = ContentService.instance();
    }

    public void init(DeltafiConfig.ActionSpec spec) {
        super.init(spec);

        // Add parameter processing here...
    }

    public Result execute(DeltaFile deltafile) {
        log.trace(name + " formatting (" + deltafile.getDid() + ")");

        FormatResult result = new FormatResult(this, deltafile.getDid(), deltafile.getSourceInfo().getFilename());

        deltafile.getSourceInfo().getMetadata().forEach(kv -> result.addMetadata("sourceInfo." + kv.getKey(), kv.getValue()));
        staticMetadata.forEach(result::addMetadata);

        result.setObjectReference(deltafile.getProtocolStack().get(0).getObjectReference());
        generateMetrics(deltafile);

        return result;
    }

    private static final MetricLogger metricLogger = new MetricLogger();
    static final String LOG_SOURCE = "format";
    static final String FILES_PROCESSED = "files_processed";

    public void generateMetrics(DeltaFile deltafile) {
        Tag[] tags = {
                new Tag("did", deltafile.getDid()),
                new Tag("flow", deltafile.getSourceInfo().getFlow()),
                new Tag("action", getClass().getSimpleName())
        };

        metricLogger.logMetric(LOG_SOURCE, MetricType.COUNTER, FILES_PROCESSED, 1, tags);
    }

}