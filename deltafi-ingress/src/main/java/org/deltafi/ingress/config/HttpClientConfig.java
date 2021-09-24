package org.deltafi.ingress.config;

import javax.enterprise.inject.Produces;
import java.net.http.HttpClient;

public class HttpClientConfig {
    @Produces
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }
}
