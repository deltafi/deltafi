package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.passthrough.param.RoteTransformParameters;

@SuppressWarnings("unused")
@Slf4j
public class RoteTransformAction extends Action<RoteTransformParameters> {

    public Result execute(DeltaFile deltafile, RoteTransformParameters params) {
        log.trace(params.getName() + " transforming (" + deltafile.getDid() + ")");

        TransformResult result = new TransformResult(params.getName(), deltafile.getDid());

        result.setType(params.getResultType());
        result.setObjectReference(deltafile.getProtocolStack().get(0).getObjectReference());
        result.addMetadata(params.getStaticMetadata());

        generateMetrics(deltafile);
        return result;
    }

    private static final MetricLogger metricLogger = new MetricLogger();
    static final String LOG_SOURCE = "transform";
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
    public Class<RoteTransformParameters> getParamType() {
        return RoteTransformParameters.class;
    }
}