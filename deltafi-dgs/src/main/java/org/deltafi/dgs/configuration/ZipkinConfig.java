package org.deltafi.dgs.configuration;

import org.deltafi.common.trace.ZipkinRestClient;
import org.deltafi.common.trace.ZipkinService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class ZipkinConfig {

    @Value("${zipkin.url:http://localhost:9411/api/v2/span}")
    private String zipkinUrl;

    @Value("${zipkin.enabled:false}")
    private final boolean enableZipkin = false;

    @Bean
    public ZipkinService zipkinService() {
        return new ZipkinService(zipkinRestClient(), enableZipkin);
    }

    public ZipkinRestClient zipkinRestClient() {
        return new ZipkinRestClient(getHttpClient(), zipkinUrl);
    }

    public HttpClient getHttpClient() {
        return HttpClient.newHttpClient();
    }
}
