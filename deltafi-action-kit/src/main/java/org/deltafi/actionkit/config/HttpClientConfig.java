package org.deltafi.actionkit.config;

import javax.enterprise.inject.Produces;
import java.net.http.HttpClient;

public class HttpClientConfig {
    @Produces
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }
}
