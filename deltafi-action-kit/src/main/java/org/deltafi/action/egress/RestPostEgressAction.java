package org.deltafi.action.egress;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.action.Result;
import org.deltafi.action.error.ErrorResult;
import org.deltafi.config.DeltafiConfig;
import org.deltafi.dgs.generated.types.FormattedData;
import org.deltafi.exception.ContentServiceConnectException;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.service.*;
import org.deltafi.types.DeltaFile;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
@Slf4j
public class RestPostEgressAction extends EgressAction{

    private String url;
    private String prefix = "";
    final ContentService contentService;
    final HttpService httpPostService;


    private static final MetricLogger metricLogger = new MetricLogger();

    @SuppressWarnings("unused")
    public RestPostEgressAction() {
        super();
        contentService = ContentService.instance();
        httpPostService = HttpService.instance();
    }

    public void init(DeltafiConfig.ActionSpec spec) {
        super.init(spec);
        // TODO: Throw exception if parameter missing...
        url = (String) spec.parameters.get("url");
        Object mp = spec.parameters.get("metadata_prefix");
        if (mp instanceof String) {
           prefix = (String) spec.parameters.get("metadata_prefix");
        }
    }

    static final String LOG_SOURCE = "egress";
    static final String FILES_OUT = "files_out";
    static final String BYTES_OUT = "bytes_out";

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

    public Result execute(DeltaFile deltafile) {
        log.debug(name + " posting (" + deltafile.getDid() + ") to: " + url );

        // TODO: Catch exceptions from post, generate error query
        try {
            // egressFeed is guaranteed to return only 1 formattedData
            FormattedData formattedData = deltafile.getFormattedData().get(0);
            Map<String, String> headers = new HashMap<>();
            if (formattedData.getMetadata() != null)
                for (var pair : formattedData.getMetadata()) headers.put(prefix + pair.getKey(), pair.getValue());
            staticMetadata.forEach((k, v) -> headers.put(prefix + k, v));
            headers.put(prefix + "did", deltafile.getDid());
            headers.put(prefix + "ingressFlow", deltafile.getSourceInfo().getFlow());
            headers.put(prefix + "flow", flow());
            headers.put(prefix + "originalFilename", deltafile.getSourceInfo().getFilename());
            headers.put(prefix + "filename", formattedData.getFilename());
            contentService.get(formattedData.getObjectReference(),
                    (InputStream is) -> {
                        HttpResponse<InputStream> response = httpPostService.post(url, headers, is);
                        generateEgressMetrics(deltafile, formattedData.getObjectReference().getSize(), url);
                    });

            log.info("Successful egress: " + this.name() + ": " + deltafile.getDid() + " (" + url +")");
            return new EgressResult(this, deltafile.getDid());
        } catch(ContentServiceConnectException e) {
            return null;
        } catch(Throwable e) {
            log.error("Unable to complete egress",e);
            return new ErrorResult(this, deltafile.getDid(), "Unable to complete egress: " + e);
        }
    }
}