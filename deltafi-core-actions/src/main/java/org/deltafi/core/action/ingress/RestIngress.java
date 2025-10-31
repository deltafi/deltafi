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
package org.deltafi.core.action.ingress;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.actionkit.action.ingress.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RestIngress extends TimedIngressAction<RestIngress.Parameters> {
    static final String DEFAULT_FILENAME = "input-file";

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Parameters extends ActionParameters {
        @JsonProperty(required = true)
        @JsonPropertyDescription("The REST url to poll")
        private String url;

        @JsonProperty
        @JsonPropertyDescription("The headers to include in the request")
        private Map<String, String> headers = new HashMap<>();
    }

    private final HttpClient httpClient;

    public RestIngress(HttpClient httpClient) {
        super(ActionOptions.builder()
                .description("Polls a REST server for files to ingress.")
                .build());
        this.httpClient = httpClient;
    }

    @Override
    public IngressResultType ingress(@NotNull ActionContext context, @NotNull RestIngress.Parameters params) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(params.getUrl()));
        if (params.getHeaders() != null) {
            params.getHeaders().forEach(requestBuilder::header);
        }

        try {
            HttpResponse<InputStream> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofInputStream());

            Response.Status status = Response.Status.fromStatusCode(response.statusCode());
            if ((status == null) || (status.getFamily() != Response.Status.Family.SUCCESSFUL)) {
                return buildUnhealthyIngressResult(context, "Bad response status: " + response.statusCode());
            }

            IngressResult ingressResult = new IngressResult(context);
            if (response.statusCode() == Response.Status.OK.getStatusCode()) {
                String filename = parseFilename(response);
                IngressResultItem resultItem = new IngressResultItem(context, filename);
                resultItem.saveContent(response.body(), filename, response.headers().map()
                        .getOrDefault(HttpHeaders.CONTENT_TYPE, List.of(MediaType.MEDIA_TYPE_WILDCARD)).getFirst());
                ingressResult.addItem(resultItem);
            }
            return ingressResult;
        } catch (IOException e) {
            return buildUnhealthyIngressResult(context, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return buildUnhealthyIngressResult(context, e.getMessage());
        }
    }

    private static final Pattern ATTACHMENT_PATTERN = Pattern.compile("attachment;\\s?filename=\"(.+)\"",
            Pattern.CASE_INSENSITIVE);

    private String parseFilename(HttpResponse<InputStream> response) {
        String filename = DEFAULT_FILENAME;
        if (response.headers().map().get(HttpHeaders.CONTENT_DISPOSITION) != null) {
            Matcher matcher = ATTACHMENT_PATTERN.matcher(
                    response.headers().map().get(HttpHeaders.CONTENT_DISPOSITION).getFirst());
            if (matcher.find()) {
                filename = matcher.group(1);
            }
        }
        return filename;
    }

    private IngressResult buildUnhealthyIngressResult(ActionContext context, String message) {
        IngressResult ingressResult = new IngressResult(context);
        ingressResult.setStatus(IngressStatus.UNHEALTHY);
        ingressResult.setStatusMessage("Unable to get file from REST URL: " + message);
        return ingressResult;
    }
}
