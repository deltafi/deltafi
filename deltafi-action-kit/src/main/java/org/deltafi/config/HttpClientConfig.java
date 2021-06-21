package org.deltafi.config;

import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Singleton;
import javax.ws.rs.Produces;
import java.net.http.HttpClient;

@Slf4j
@Singleton
public class HttpClientConfig {
    @Produces
    @ApplicationScoped
    public HttpClient getHttpClient() {
        return HttpClient.newHttpClient();
    }
}
