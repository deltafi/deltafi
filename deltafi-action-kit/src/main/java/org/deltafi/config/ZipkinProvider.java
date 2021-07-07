package org.deltafi.config;

import org.deltafi.common.trace.ZipkinConfig;
import org.deltafi.common.trace.ZipkinRestClient;
import org.deltafi.common.trace.ZipkinService;

import javax.inject.Singleton;
import javax.ws.rs.Produces;
import java.net.http.HttpClient;

@Singleton
public class ZipkinProvider {

    @Produces
    @Singleton
    public ZipkinService zipkinService(HttpClient httpClient, DeltafiConfig config) {
        ZipkinConfig zipkinConfig = config.getZipkin();
        return new ZipkinService(zipkinRestClient(httpClient, zipkinConfig.getUrl()), zipkinConfig);
    }

    public ZipkinRestClient zipkinRestClient(HttpClient httpClient, String url) {
        return new ZipkinRestClient(httpClient, url);
    }
}
