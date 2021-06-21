package org.deltafi.action.format;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.action.Action;
import org.deltafi.config.DeltafiConfig;
import org.deltafi.dgs.generated.client.ActionFeedGraphQLQuery;
import org.deltafi.dgs.generated.client.ActionFeedProjectionRoot;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.types.DeltaFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
abstract public class FormatAction implements Action {

    private static final MetricLogger metricLogger = new MetricLogger();
    static final String LOG_SOURCE = "format";
    static final String FILES_PROCESSED = "files_processed";

    protected String name;
    protected final Map<String,String> staticMetadata = new HashMap<>();

    @Override
    public BaseProjectionNode getProjection() {
        return new ActionFeedProjectionRoot()
                .did()
                .domains()
                    .domainTypes()
                .parent()
                .enrichment()
                    .enrichmentTypes()
                .parent()
                .protocolStack()
                    .type()
                    .objectReference()
                        .size()
                        .offset()
                        .bucket()
                        .name()
                    .parent()
                    .metadata()
                        .key()
                        .value()
                    .parent()
                .parent()
                .sourceInfo()
                    .filename()
                    .flow()
                    .metadata()
                        .key()
                        .value()
                    .parent()
                .parent();
        }

    @Override
    public void init(DeltafiConfig.ActionSpec spec) {
        name = spec.name;
        Action.addStaticMetadata(spec, staticMetadata, log);
    }

    @Override
    public String name() {
        return name;
    }

    @SuppressWarnings("unused")
    static public void addSourceInputMetadata(FormatResult result, DeltaFile deltaFile) {
        deltaFile.getSourceInfo().getMetadata().forEach(kv -> result.addMetadata("sourceInfo." + kv.getKey(),kv.getValue()));
    }

    @SuppressWarnings("unused")
    static public void addProtocolStackMetadata(FormatResult result, DeltaFile deltaFile) {
        deltaFile.getProtocolStack().forEach( ps -> result.addMetadata(ps.getMetadata()));
    }

    public void generateMetrics(DeltaFile deltafile) {
        Tag[] tags = {
                new Tag("did", deltafile.getDid()),
                new Tag("flow", deltafile.getSourceInfo().getFlow()),
                new Tag("action", name())
        };

        metricLogger.logMetric(LOG_SOURCE, MetricType.COUNTER, FILES_PROCESSED, 1, tags);
    }
}