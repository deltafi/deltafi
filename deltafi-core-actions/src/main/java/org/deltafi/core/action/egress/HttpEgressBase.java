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
import org.deltafi.actionkit.action.egress.*;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.http.HttpSendException;
import org.deltafi.common.http.HttpService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.*;

@Slf4j
public class HttpEgressBase<P extends ActionParameters & IHttpEgressParameters> extends EgressAction<P> {
    protected final HttpService httpService;

    public HttpEgressBase(String description, HttpService httpService) {
        super(ActionOptions.builder()
                .description(description)
                .build());
        this.httpService = httpService;
    }

    public EgressResultType egress(@NotNull ActionContext context, @NotNull P params, @NotNull EgressInput input) {
        return doEgress(context, params, HttpRequestMethod.POST, input);
    }

    public EgressResultType doEgress(@NotNull ActionContext context, @NotNull P params, @NotNull HttpRequestMethod method, @NotNull EgressInput input) {
        HttpRequest request;
        try {
            HttpRequest.Builder httpRequestBuilder = HttpService.newRequestBuilder(params.getUrl(), buildHeaders(context, params, input), getMediaType(input));
            switch (method) {
                case PATCH -> httpRequestBuilder.method("PATCH", bodyPublisher(context, input));
                case POST -> httpRequestBuilder.POST(bodyPublisher(context, input));
                case PUT -> httpRequestBuilder.PUT(bodyPublisher(context, input));
                case DELETE -> httpRequestBuilder.DELETE();
            }
            request = httpRequestBuilder.build();
        } catch (JsonProcessingException e) {
            return new ErrorResult(context, "Unable to build " + method + " headers", e).logErrorTo(log);
        } catch (IOException e) {
            return new ErrorResult(context, "Unable to prepare request body for " + method, e).logErrorTo(log);
        } catch (Exception e) {
            return new ErrorResult(context, "Unexpected error building request for " + method, e.getMessage(), e).logErrorTo(log);
        }

        // --- First Attempt (Optimistic: Discard body to reduce latency) ---
        HttpResponse<Void> initialResponse;
        try {
            initialResponse = httpService.execute(request, HttpResponse.BodyHandlers.discarding());
        } catch (HttpSendException e) {
            // Network or send error on the first attempt, no HTTP status to check.
            return new ErrorResult(context, "Service " + method + " failure", e.getMessage(), e.getCause() != null ? e.getCause() : e).logErrorTo(log);
        }

        Response.Status initialStatus = Response.Status.fromStatusCode(initialResponse.statusCode());
        if (initialStatus != null && initialStatus.getFamily() == Response.Status.Family.SUCCESSFUL) {
            return new EgressResult(context);
        }

        // --- Error on First Attempt: Log and Prepare to Retry for Body ---
        String initialErrorMsg = "Initial " + method + " attempt returned unsuccessful HTTP status: " + initialResponse.statusCode();
        log.warn("{}. Retrying to capture error response body for DID: {}. URL: {}", initialErrorMsg, context.getDid(), request.uri());

        // --- Second Attempt (Get body so if it's still an error, we can record the error message) ---
        try {
            HttpResponse<InputStream> retryResponse = httpService.execute(request, HttpResponse.BodyHandlers.ofInputStream());
            int retryStatusCode = retryResponse.statusCode();

            try (InputStream responseBodyStream = retryResponse.body()) {
                Response.Status retryHttpStatus = Response.Status.fromStatusCode(retryStatusCode);
                if (retryHttpStatus != null && retryHttpStatus.getFamily() == Response.Status.Family.SUCCESSFUL) {
                    log.info("For DID {}: {} to {} succeeded on retry (HTTP {}) after initial failure (HTTP {}).",
                            context.getDid(), method, request.uri(), retryStatusCode, initialResponse.statusCode());
                    return new EgressResult(context);
                }

                String errorBodyContent = "Error response body could not be read or was empty.";
                if (responseBodyStream != null) {
                    try {
                        errorBodyContent = new String(responseBodyStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); // Or appropriate charset
                    } catch (IOException readEx) {
                        errorBodyContent = "Failed to read error response body: " + readEx.getMessage();
                    }
                }
                return new ErrorResult(context, "Unsuccessful HTTP " + method + ": " + retryResponse.statusCode(), errorBodyContent).logErrorTo(log);
            }
        } catch (HttpSendException e) {
            return new ErrorResult(context, initialErrorMsg + ". Retry attempt to get error body also failed: " + e.getMessage(), e.getCause() != null ? e.getCause() : e).logErrorTo(log);
        } catch (IOException e) {
            return new ErrorResult(context, initialErrorMsg + ". IO Error during retry attempt for error body: " + e.getMessage(), e).logErrorTo(log);
        } catch (Exception e) {
            return new ErrorResult(context, "Unexpected error during retry attempt for " + method, e.getMessage(), e).logErrorTo(log);
        }
    }

    protected BodyPublisher bodyPublisher(@NotNull ActionContext context, @NotNull EgressInput input) throws IOException {
        if (input.getContent().getSize() < 1) {
            return BodyPublishers.noBody();
        }

        return HttpRequest.BodyPublishers.fromPublisher(BodyPublishers.ofInputStream(() -> {
            try {
                return this.openInputStream(context, input);
            } catch (IOException e) {
                log.error("Failed to open input stream", e);
                throw new UncheckedIOException(e);
            }
        }), input.getContent().getSize());
    }

    protected InputStream openInputStream(@NotNull ActionContext context, @NotNull EgressInput input)
            throws IOException {
        return input.getContent().loadInputStream();
    }

    protected String getMediaType(@NotNull EgressInput input) {
        return input.getContent().getMediaType();
    }

    protected Map<String, String> buildHeaders(@NotNull ActionContext context, @NotNull P params,
                                               @NotNull EgressInput input) throws JsonProcessingException {
        return Collections.emptyMap();
    }
}
