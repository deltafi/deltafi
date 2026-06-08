/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.s3;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import software.amazon.awssdk.core.retry.RetryMode;

import java.net.URI;

@Data
// callSuper = true caused equality to always return false despite the param values being equal
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public class S3ClientParams extends ActionParameters {

    @JsonProperty(defaultValue = "true")
    @JsonPropertyDescription("Forces this client to use path-style addressing for buckets. Required to be true for MinIO and some S3-compatible services.")
    private boolean forcePathStyle = true;

    @JsonProperty
    @JsonPropertyDescription("Custom S3 endpoint URL. Leave empty to use AWS S3. For MinIO, use format: http://hostname:9000")
    private String endpoint;

    @JsonProperty(defaultValue = "us-east-1")
    @JsonPropertyDescription("AWS region or S3-compatible service region. Examples: us-east-1, us-west-2, eu-west-1")
    private String region = "us-east-1";

    @JsonProperty
    @JsonPropertyDescription("Access key for S3 authentication. Leave empty to use default credential provider chain.")
    private String accessKey;

    @JsonProperty
    @JsonPropertyDescription("Secret key for S3 authentication. Leave empty to use default credential provider chain.")
    private String secretKey;

    @JsonProperty
    @JsonPropertyDescription("Session token for temporary credentials (optional). Used with STS assumed roles.")
    private String sessionToken;

    @JsonProperty(defaultValue = "30")
    @JsonPropertyDescription("Connection timeout in seconds for S3 client connections.")
    private int connectionTimeoutSeconds = 30;

    @JsonProperty(defaultValue = "300")
    @JsonPropertyDescription("Socket timeout in seconds for S3 client read operations.")
    private int socketTimeoutSeconds = 300;

    @JsonProperty(defaultValue = "3")
    @JsonPropertyDescription("Maximum number of retry attempts for failed S3 operations.")
    private int maxRetries = 3;

    @JsonProperty(defaultValue = "STANDARD")
    @JsonPropertyDescription("Set the RetryMode for failed requests. Legacy - 3 retries, no backoff, Standard - 2 retries, with backoff, Adaptive - 2 retries with adaptive backoff ")
    private RetryMode retryMode = RetryMode.STANDARD;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Enable accelerated transfer for S3 operations (AWS S3 only, not supported by MinIO).")
    private boolean accelerateMode;

    @JsonProperty(defaultValue = "true")
    @JsonPropertyDescription("Enable SSL/TLS for HTTP connections. Only recommended turning off for local development.")
    private boolean enableSSL = true;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Enable chunked encoding for uploads. Disable for compatibility with some S3-compatible services.")
    private boolean chunkedEncodingDisabled;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Enable checksum validation for all S3 operations.")
    private boolean checksumValidationEnabled;

    @JsonProperty
    @JsonPropertyDescription("Profile name to use from AWS credentials file (~/.aws/credentials). Leave empty to use default profile.")
    private String profileName;

    /**
     * Validates the S3 configuration parameters
     *
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate() {
        if (StringUtils.isBlank(region)) {
            throw new IllegalArgumentException("Region cannot be null or empty");
        }

        if (connectionTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Connection timeout must be positive");
        }

        if (socketTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Socket timeout must be positive");
        }

        if (maxRetries < 0) {
            throw new IllegalArgumentException("Max retries cannot be negative");
        }

        // Validate endpoint format if provided
        if (StringUtils.isNotBlank(endpoint)) {
            try {
                URI ignored = URI.create(endpoint.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid endpoint URL format: " + endpoint, e);
            }
        }

        // Validate AWS-specific features when using custom endpoint
        if (StringUtils.isNotBlank(endpoint) && accelerateMode) {
            throw new IllegalArgumentException("Accelerate mode is not supported with custom endpoints (MinIO, etc.)");
        }
    }
}
