package org.deltafi.core.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.core.parameters.RestPostEgressParameters;
import org.deltafi.actionkit.exception.ContentServiceConnectException;
import org.deltafi.actionkit.service.ContentService;
import org.deltafi.actionkit.service.HttpService;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.FormattedData;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
@Slf4j
public class RestPostEgressAction extends EgressAction<RestPostEgressParameters> {

    private static final MetricLogger metricLogger = new MetricLogger();

    static final String LOG_SOURCE = "egress";
    static final String FILES_OUT = "files_out";
    static final String BYTES_OUT = "bytes_out";

    final ContentService contentService;
    final HttpService httpPostService;

    public RestPostEgressAction(ContentService contentService, HttpService httpPostService) {
        this.contentService = contentService;
        this.httpPostService = httpPostService;
    }

    void generateEgressMetrics(DeltaFile deltafile, long size, String endpoint) {
        Tag[] tags = {
                new Tag("did", deltafile.getDid()),
                new Tag("flow", deltafile.getSourceInfo().getFlow()),
                new Tag("endpoint", endpoint),
                new Tag("action", getClass().getSimpleName())
        };

        metricLogger.logMetric(LOG_SOURCE, MetricType.COUNTER, FILES_OUT, 1, tags);
        metricLogger.logMetric(LOG_SOURCE, MetricType.COUNTER, BYTES_OUT, size, tags);
    }

    public Result execute(DeltaFile deltafile, RestPostEgressParameters params) {
        log.debug(params.getName() + " posting (" + deltafile.getDid() + ") to: " + params.getUrl() );

        // TODO: Catch exceptions from post, generate error query
        try {
            String prefix = params.getMetadataPrefix();
            String url = params.getUrl();

            // egressFeed is guaranteed to return only 1 formattedData
            FormattedData formattedData = deltafile.getFormattedData().get(0);
            Map<String, String> headers = new HashMap<>();
            if (formattedData.getMetadata() != null)
                for (var pair : formattedData.getMetadata()) headers.put(prefix + pair.getKey(), pair.getValue());
            params.getStaticMetadata().forEach((k, v) -> headers.put(prefix + k, v));
            headers.put(prefix + "did", deltafile.getDid());
            headers.put(prefix + "ingressFlow", deltafile.getSourceInfo().getFlow());
            headers.put(prefix + "flow", params.getEgressFlow());
            headers.put(prefix + "originalFilename", deltafile.getSourceInfo().getFilename());
            headers.put(prefix + "filename", formattedData.getFilename());
            contentService.get(formattedData.getObjectReference(),
                    (InputStream is) -> {
                        HttpResponse<InputStream> response = httpPostService.post(url, headers, is);
                        generateEgressMetrics(deltafile, formattedData.getObjectReference().getSize(), url);
                    });

            log.info("Successful egress: " + params.getName() + ": " + deltafile.getDid() + " (" + url +")");
            return new EgressResult(params.getName(), deltafile.getDid());
        } catch(ContentServiceConnectException e) {
            return null;
        } catch(Throwable e) {
            return new ErrorResult(params.getName(), deltafile, "Unable to complete egress", e).logErrorTo(log);
        }
    }

    @Override
    public Class<RestPostEgressParameters> getParamType() {
        return RestPostEgressParameters.class;
    }
}