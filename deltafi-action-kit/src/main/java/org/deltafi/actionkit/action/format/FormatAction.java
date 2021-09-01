package org.deltafi.actionkit.action.format;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.core.domain.api.types.DeltaFile;

@Slf4j
public abstract class FormatAction<P extends ActionParameters> extends Action<P> {

    private static final MetricLogger metricLogger = new MetricLogger();
    static final String LOG_SOURCE = "format";
    static final String FILES_PROCESSED = "files_processed";

    @SuppressWarnings("unused")
    static public void addSourceInputMetadata(FormatResult result, DeltaFile deltaFile) {
        if(deltaFile.getSourceInfo() != null && deltaFile.getSourceInfo().getMetadata() != null) {
            deltaFile.getSourceInfo().getMetadata().forEach(kv -> result.addMetadata("sourceInfo." + kv.getKey(), kv.getValue()));
        }
    }

    @SuppressWarnings("unused")
    static public void addProtocolStackMetadata(FormatResult result, DeltaFile deltaFile) {
        if(deltaFile.getProtocolStack() != null) {
            deltaFile.getProtocolStack().forEach(ps -> result.addMetadata(ps.getMetadata()));
        }
    }

    public void generateMetrics(DeltaFile deltafile, String name) {
        Tag[] tags = {
                new Tag("did", deltafile.getDid()),
                new Tag("flow", deltafile.getSourceInfo().getFlow()),
                new Tag("action", name)
        };

        metricLogger.logMetric(LOG_SOURCE, MetricType.COUNTER, FILES_PROCESSED, 1, tags);
    }
}