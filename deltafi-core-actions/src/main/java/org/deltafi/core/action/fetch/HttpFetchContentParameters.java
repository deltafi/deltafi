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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.Map;
import java.util.Set;

/**
 * Parameters for the HttpFetchToContent action.
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class HttpFetchContentParameters extends ActionParameters {

    @JsonProperty(required = true)
    @JsonPropertyDescription("The URL to fetch content from.")
    private String url;

    @JsonProperty(defaultValue = "GET")
    @JsonPropertyDescription("The HTTP method to use when making the request. Default is GET.")
    private String httpMethod = "GET";

    @JsonPropertyDescription("Optional request body for POST and PUT requests. Ignored for GET, DELETE, and HEAD.")
    private String requestBody;

    @JsonPropertyDescription("HTTP headers to set in the request.")
    private Map<String, String> requestHeaders;

    @JsonPropertyDescription("The media type of the fetched content. If not specified, it will be inferred from the HTTP response headers.")
    private String mediaType;

    @JsonPropertyDescription("The name to assign to the fetched content. If not specified, the filename will be extracted from the Content-Disposition header or default to 'fetched-file'.")
    private String contentName;

    @JsonPropertyDescription("The annotation name where the HTTP response code should be stored.")
    private String responseCodeAnnotationName;

    @JsonPropertyDescription("If set, response headers will be stored in metadata using this key.")
    private String responseHeadersMetadataKey;

    @JsonProperty(defaultValue = "5000")
    @JsonPropertyDescription("The timeout (in milliseconds) for establishing an HTTP connection. Default is 5000ms.")
    private int connectTimeout = 5000;

    @JsonProperty(defaultValue = "10000")
    @JsonPropertyDescription("The timeout (in milliseconds) for reading data from the HTTP connection. Default is 10000ms.")
    private int readTimeout = 10000;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Whether to replace the existing content in the DeltaFile. If false, the new content is added while retaining the existing content. Default is false.")
    private boolean replaceExistingContent = false;

    @JsonPropertyDescription("A list of tags to assign to the fetched content.")
    private Set<String> tags;
}