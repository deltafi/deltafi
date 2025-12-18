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
package org.deltafi.core.action.base64;

// ABOUTME: Parameters for Base64Encode action.
// ABOUTME: Configures URL-safe encoding and output media type.

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.core.action.ContentSelectionParameters;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Base64EncodeParameters extends ContentSelectionParameters {
    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Use URL-safe Base64 encoding (uses - and _ instead of + and /)")
    private boolean urlSafe = false;

    @JsonProperty(defaultValue = "text/plain")
    @JsonPropertyDescription("Media type of the encoded content")
    private String outputMediaType = "text/plain";
}
