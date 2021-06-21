package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.action.Result;
import org.deltafi.action.transform.TransformAction;
import org.deltafi.action.transform.TransformResult;
import org.deltafi.config.DeltafiConfig;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.service.ContentService;
import org.deltafi.types.DeltaFile;

import java.util.Objects;

@SuppressWarnings("unused")
@Slf4j
public class RoteTransformAction extends TransformAction {

    final ContentService contentService;

    String resultType;

    @SuppressWarnings("unused")
    public RoteTransformAction() {
        super();
        contentService = ContentService.instance();
    }

    public void init(DeltafiConfig.ActionSpec spec) {
        super.init(spec);

        if (Objects.nonNull(spec.parameters) && spec.parameters.containsKey("result_type")) {
            resultType = (String) spec.parameters.get("result_type");
        } else {
            throw new RuntimeException("TransformAction " + spec.name + " requires result_type parameter");
        }
    }

    public Result execute(DeltaFile deltafile) {
        log.trace(name + " transforming (" + deltafile.getDid() + ")");

        TransformResult result = new TransformResult(this, deltafile.getDid());

        result.setType(resultType);
        result.setObjectReference(deltafile.getProtocolStack().get(0).getObjectReference());
        result.addMetadata(staticMetadata);

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
}