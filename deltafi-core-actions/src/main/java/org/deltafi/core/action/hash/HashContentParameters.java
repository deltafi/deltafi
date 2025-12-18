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
package org.deltafi.core.action.hash;

// ABOUTME: Parameters for HashContent action.
// ABOUTME: Configures hash algorithm and metadata key pattern.

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.core.action.ContentMatchingParameters;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class HashContentParameters extends ContentMatchingParameters {
    @JsonProperty(defaultValue = "SHA-256")
    @JsonPropertyDescription("Hash algorithm to use")
    private HashAlgorithm algorithm = HashAlgorithm.SHA_256;

    @JsonProperty(defaultValue = "hash")
    @JsonPropertyDescription("Metadata key for the hash value. For multiple content pieces, " +
            "the content index is appended (e.g., 'hash.0', 'hash.1')")
    private String metadataKey = "hash";

    public enum HashAlgorithm {
        @JsonProperty("MD5") MD5("MD5"),
        @JsonProperty("SHA-1") SHA_1("SHA-1"),
        @JsonProperty("SHA-256") SHA_256("SHA-256"),
        @JsonProperty("SHA-384") SHA_384("SHA-384"),
        @JsonProperty("SHA-512") SHA_512("SHA-512");

        private final String algorithmName;

        HashAlgorithm(String algorithmName) {
            this.algorithmName = algorithmName;
        }

        public String getAlgorithmName() {
            return algorithmName;
        }
    }
}
