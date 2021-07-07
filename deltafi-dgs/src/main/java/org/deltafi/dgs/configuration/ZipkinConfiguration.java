package org.deltafi.dgs.configuration;

import org.deltafi.common.trace.ZipkinRestClient;
import org.deltafi.common.trace.ZipkinService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class ZipkinConfiguration {

    DeltaFiProperties properties;

    public ZipkinConfiguration(DeltaFiProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ZipkinService zipkinService() {
        return new ZipkinService(zipkinRestClient(), properties.getZipkin());
    }

    public ZipkinRestClient zipkinRestClient() {
        return new ZipkinRestClient(getHttpClient(), properties.getZipkin().getUrl());
    }

    public HttpClient getHttpClient() {
        return HttpClient.newHttpClient();
    }
}
