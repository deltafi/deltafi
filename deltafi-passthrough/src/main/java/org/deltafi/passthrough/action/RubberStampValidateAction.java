package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.SimpleAction;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.validate.ValidateResult;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.dgs.api.types.DeltaFile;

@SuppressWarnings("unused")
@Slf4j
public class RubberStampValidateAction extends SimpleAction {

    public Result execute(DeltaFile deltafile, ActionParameters params) {
        log.trace(params.getName() + " validating (" + deltafile.getDid() + ")");
        generateMetrics(deltafile);
        return new ValidateResult(params.getName(), deltafile.getDid());
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