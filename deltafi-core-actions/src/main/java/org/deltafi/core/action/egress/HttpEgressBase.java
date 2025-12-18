/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.action.egress;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.parameters.EnvVar;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

@Slf4j
public class HttpEgressBase<P extends ActionParameters & IHttpEgressParameters> extends EgressAction<P> {
    protected final OkHttpClient httpClient;

    public HttpEgressBase(String description, OkHttpClient httpClient) {
        super(ActionOptions.builder()
                .description(description)
                .build());
        this.httpClient = httpClient;
    }

    public EgressResultType egress(@NotNull ActionContext context, @NotNull P params, @NotNull EgressInput input) {
        return doEgress(context, params, HttpRequestMethod.POST, input);
    }

    public EgressResultType doEgress(@NotNull ActionContext context, @NotNull P params, @NotNull HttpRequestMethod method, @NotNull EgressInput input) {
        if (input.getContent() == null) {
            NoContentPolicy policy = params.getNoContentPolicy();

            switch (policy) {
                case FILTER:
                    return new FilterResult(context, "Content is null - filtered by noContentPolicy");
                case ERROR:
                    return new ErrorResult(context, "Cannot perform egress: no content available",
                            "The egress action requires content but none was provided").logErrorTo(log);
                case SEND_EMPTY:
                    break;
                default:
                    return new ErrorResult(context, "Unknown noContentPolicy: " + policy,
                            "The noContentPolicy value is not recognized").logErrorTo(log);
            }
        }

        try {
            OkHttpClient clientToUse = getClientWithProxy(params);
            Request request = buildOkHttpRequest(context, params, input, method);
            try (Response response = clientToUse.newCall(request).execute()) {
                return response.isSuccessful() ? new EgressResult(context) : processError(context, response, method);
            }
        } catch (IOException e) {
            return new ErrorResult(context, "Service " + method + " failure", e.getMessage(), e).logErrorTo(log);
        } catch (Exception e) {
            return new ErrorResult(context, "Unexpected error during " + method + " request", e.getMessage(), e).logErrorTo(log);
        }
    }

    private OkHttpClient getClientWithProxy(P params) {
        String proxyUrl = params.getProxyUrl();
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return httpClient;
        }

        URI proxyUri = URI.create(proxyUrl);
        int port = proxyUri.getPort();
        if (port == -1) {
            port = proxyUri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
        }

        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUri.getHost(), port));
        OkHttpClient.Builder builder = httpClient.newBuilder().proxy(proxy);

        String username = params.getProxyUsername();
        EnvVar passwordEnvVar = params.getProxyPassword();
        if (username != null && !username.isBlank() && passwordEnvVar != null && passwordEnvVar.isSet()) {
            String password = passwordEnvVar.resolve();
            builder.proxyAuthenticator((route, response) -> {
                String credential = Credentials.basic(username, password);
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            });
        }

        return builder.build();
    }

    private Request buildOkHttpRequest(@NotNull ActionContext context, @NotNull P params, @NotNull EgressInput input, @NotNull HttpRequestMethod method) throws IOException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(params.getUrl());

        Map<String, String> headers = buildHeaders(context, params, input);
        headers.forEach(requestBuilder::addHeader);

        switch (method) {
            case POST -> requestBuilder.post(prepareRequestBody(context, params, input));
            case PUT -> requestBuilder.put(prepareRequestBody(context, params, input));
            case PATCH -> requestBuilder.patch(prepareRequestBody(context, params, input));
            case DELETE -> requestBuilder.delete();
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        return requestBuilder.build();
    }

    private ErrorResult processError(ActionContext context, Response response, HttpRequestMethod method) {
        String errorBodyContent = "Error response body could not be read or was empty.";
        if (response.body() != null) {
            try {
                errorBodyContent = response.body().string();
            } catch (IOException e) {
                errorBodyContent = "Failed to read error response body: " + e.getMessage();
            }
        }

        return new ErrorResult(context, "Unsuccessful HTTP " + method + ": " + response.code(), errorBodyContent).logErrorTo(log);
    }

    protected RequestBody prepareRequestBody(ActionContext context, P params, EgressInput input) {
        return new InputStreamRequestBody(input);
    }

    protected Map<String, String> buildHeaders(@NotNull ActionContext context, @NotNull P params,
                                               @NotNull EgressInput input) throws JsonProcessingException {
        return Collections.emptyMap();
    }
}
