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
package org.deltafi.core.action.fetch;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.types.ActionContext;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class HttpFetchContent extends TransformAction<HttpFetchContentParameters> {
    public static final String DEFAULT_FILENAME = "fetched-file";
    private static final Pattern ATTACHMENT_PATTERN = Pattern.compile("attachment;\\s?filename=\"(.+)\"", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;

    public HttpFetchContent(HttpClient httpClient) {
        super("Fetch binary content from a given URL and store it as DeltaFile content.");
        this.httpClient = httpClient;
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull HttpFetchContentParameters params,
                                         @NotNull TransformInput input) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(params.getUrl()))
                .timeout(java.time.Duration.ofMillis(params.getReadTimeout()));

        switch (params.getHttpMethod().toUpperCase()) {
            case "POST":
                requestBuilder.POST(params.getRequestBody() != null ?
                        HttpRequest.BodyPublishers.ofString(params.getRequestBody()) :
                        HttpRequest.BodyPublishers.noBody());
                break;
            case "PUT":
                requestBuilder.PUT(params.getRequestBody() != null ?
                        HttpRequest.BodyPublishers.ofString(params.getRequestBody()) :
                        HttpRequest.BodyPublishers.noBody());
                break;
            case "HEAD":
                requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                break;
            case "DELETE":
                requestBuilder.DELETE();
                break;
            case "GET":
            default:
                requestBuilder.GET();
                break;
        }

        if (params.getRequestHeaders() != null && !params.getRequestHeaders().isEmpty()) {
            params.getRequestHeaders().forEach(requestBuilder::header);
        }

        HttpRequest request = requestBuilder.build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int responseCode = response.statusCode();

            if (responseCode != 200) {
                String responseBody;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    responseBody = reader.lines().collect(Collectors.joining("\n"));
                }
                ErrorResult errorResult = new ErrorResult(context,
                        "HTTP request failed with response code " + responseCode,
                        "Response code: " + responseCode + " for URL: " + params.getUrl() + "\n" + responseBody)
                        .logErrorTo(log);

                if (params.getResponseCodeAnnotationName() != null) {
                    errorResult.addAnnotation(params.getResponseCodeAnnotationName(), String.valueOf(responseCode));
                }

                return errorResult;
            }

            String filename = params.getContentName() != null ? params.getContentName() : parseFilename(response);

            String mediaType = params.getMediaType() != null ?
                    params.getMediaType() :
                    response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(MediaType.MEDIA_TYPE_WILDCARD);

            TransformResult result = new TransformResult(context);

            if (params.getResponseHeadersMetadataKey() != null && !params.getResponseHeadersMetadataKey().isEmpty()) {
                String headersString = response.headers().map()
                        .entrySet()
                        .stream()
                        .map(entry -> entry.getKey() + ": " + String.join(", ", entry.getValue()))
                        .collect(Collectors.joining("\n"));

                result.addMetadata(params.getResponseHeadersMetadataKey(), headersString);
            }

            if (!params.isReplaceExistingContent()) {
                result.addContent(input.getContent());
            }

            if (params.getResponseCodeAnnotationName() != null) {
                result.addAnnotation(params.getResponseCodeAnnotationName(), String.valueOf(responseCode));
            }

            ActionContent newContent = result.saveContent(response.body(), filename, mediaType);

            if (params.getTags() != null && !params.getTags().isEmpty()) {
                newContent.addTags(new HashSet<>(params.getTags()));
            }

            return result;
        } catch (IOException | InterruptedException e) {
            return new ErrorResult(context, "Failed to fetch content", e).logErrorTo(log);
        }
    }

    private String parseFilename(HttpResponse<InputStream> response) {
        return response.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION)
                .map(header -> {
                    Matcher matcher = ATTACHMENT_PATTERN.matcher(header);
                    return matcher.find() ? matcher.group(1) : DEFAULT_FILENAME;
                })
                .orElse(DEFAULT_FILENAME);
    }
}