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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
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
        try {
            Request request = buildOkHttpRequest(context, params, input, method);
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful() ? new EgressResult(context) : processError(context, response, method);
            }
        } catch (IOException e) {
            return new ErrorResult(context, "Service " + method + " failure", e.getMessage(), e).logErrorTo(log);
        } catch (Exception e) {
            return new ErrorResult(context, "Unexpected error during " + method + " request", e.getMessage(), e).logErrorTo(log);
        }
    }

    private Request buildOkHttpRequest(@NotNull ActionContext context, @NotNull P params, @NotNull EgressInput input, @NotNull HttpRequestMethod method) throws IOException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(params.getUrl());

        Map<String, String> headers = buildHeaders(context, params, input);
        headers.forEach(requestBuilder::addHeader);

        String mediaType = getMediaType(input);
        if (mediaType != null) {
            requestBuilder.addHeader("Content-Type", mediaType);
        }

        switch (method) {
            case POST -> requestBuilder.post(prepareRequestBody(context, input));
            case PUT -> requestBuilder.put(prepareRequestBody(context, input));
            case PATCH -> requestBuilder.patch(prepareRequestBody(context, input));
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

    protected RequestBody prepareRequestBody(ActionContext context, EgressInput input){
        return new InputStreamRequestBody(input);
    }

    protected String getMediaType(@NotNull EgressInput input) {
        return input.getContent().getMediaType();
    }

    protected Map<String, String> buildHeaders(@NotNull ActionContext context, @NotNull P params,
                                               @NotNull EgressInput input) throws JsonProcessingException {
        return Collections.emptyMap();
    }
}
