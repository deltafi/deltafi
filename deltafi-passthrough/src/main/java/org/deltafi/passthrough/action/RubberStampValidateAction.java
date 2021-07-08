package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.validate.ValidateAction;
import org.deltafi.actionkit.action.validate.ValidateResult;
import org.deltafi.actionkit.config.DeltafiConfig;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.actionkit.service.ContentService;
import org.deltafi.actionkit.types.DeltaFile;

@SuppressWarnings("unused")
@Slf4j
public class RubberStampValidateAction extends ValidateAction {

    final ContentService contentService;

    @SuppressWarnings("unused")
    public RubberStampValidateAction() {
        super();
        contentService = ContentService.instance();
    }

    public void init(DeltafiConfig.ActionSpec spec) {
        super.init(spec);

        // Add parameter processing here...
    }

    public Result execute(DeltaFile deltafile) {
        log.trace(name + " validating (" + deltafile.getDid() + ")");
        generateMetrics(deltafile);
        return new ValidateResult(this, deltafile.getDid());
    }

    private static final MetricLogger metricLogger = new MetricLogger();
    static final String LOG_SOURCE = "validate";
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