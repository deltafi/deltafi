package org.deltafi.config;

import org.deltafi.common.trace.ZipkinRestClient;
import org.deltafi.common.trace.ZipkinService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Produces;
import java.net.http.HttpClient;

@Singleton
public class ZipkinConfig {

    @Inject
    HttpClient httpClient;

    @ConfigProperty(name = "zipkin.url")
    String zipkinUrl;

    @ConfigProperty(name = "zipkin.enabled", defaultValue = "true")
    boolean enableZipkin;

    @Produces
    @Singleton
    public ZipkinService zipkinService() {
        return new ZipkinService(zipkinRestClient(), enableZipkin);
    }

    public ZipkinRestClient zipkinRestClient() {
        return new ZipkinRestClient(httpClient, zipkinUrl);
    }
}
