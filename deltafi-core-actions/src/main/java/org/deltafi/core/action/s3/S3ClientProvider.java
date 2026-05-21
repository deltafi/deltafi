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

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.deltafi.common.ssl.SslCredentials;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.api.RetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class S3ClientProvider {
    private final SslCredentials sslCredentials;

    @Cacheable("s3-clients")
    public S3Client getS3Client(S3ClientParams params) {
        params.validate();

        if (params.isEnableSSL() && !sslCredentials.isConfigured()) {
            throw new IllegalStateException("SSL is not configured for this plugin and the S3Params has not disabled SSL");
        }

        S3ClientBuilder builder = S3Client.builder();

        if (StringUtils.isNotBlank(params.getRegion())) {
            builder.region(Region.of(params.getRegion()));
        }

        if (StringUtils.isNotBlank(params.getEndpoint())) {
            try {
                URI endpointUri = URI.create(params.getEndpoint().trim());
                builder.endpointOverride(endpointUri);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid endpoint URL: " + params.getEndpoint(), e);
            }
        }

        builder.forcePathStyle(params.isForcePathStyle());

        builder.credentialsProvider(createCredentialsProvider(params));

        if (params.isAccelerateMode()) {
            builder.accelerate(true);
        }

        RetryStrategy retryStrategy = AwsRetryStrategy.forRetryMode(params.getRetryMode())
                .toBuilder()
                .maxAttempts(params.getMaxRetries())
                .build();
        ClientOverrideConfiguration config = ClientOverrideConfiguration.builder()
                .retryStrategy(retryStrategy)
                .apiCallTimeout(Duration.ofSeconds(params.getSocketTimeoutSeconds()))
                .apiCallAttemptTimeout(Duration.ofSeconds(params.getConnectionTimeoutSeconds()))
                .build();
        builder.overrideConfiguration(config);

        if (params.isEnableSSL()) {
            builder.httpClientBuilder(ApacheHttpClient.builder()
                    .socketFactory(new SSLConnectionSocketFactory(sslCredentials.createSslContext())));
        }

        builder.requestChecksumCalculation(params.isChecksumValidationEnabled() ?
                RequestChecksumCalculation.WHEN_SUPPORTED : RequestChecksumCalculation.WHEN_REQUIRED);

        return builder.build();
    }

    private AwsCredentialsProvider createCredentialsProvider(S3ClientParams params) {
        if (StringUtils.isNoneBlank(params.getAccessKey(), params.getSecretKey())) {
            String accessKey = params.getAccessKey().trim();
            String secretKey = params.getSecretKey().trim();
            AwsCredentials credentials = StringUtils.isNotBlank(params.getSessionToken()) ?
                    AwsSessionCredentials.create(accessKey, secretKey, params.getSessionToken().trim()) :
                    AwsBasicCredentials.create(accessKey, secretKey);
            return StaticCredentialsProvider.create(credentials);
        }

        DefaultCredentialsProvider.Builder credentialsBuilder = DefaultCredentialsProvider.builder();
        if (StringUtils.isNotBlank(params.getProfileName())) {
            credentialsBuilder.profileName(params.getProfileName().trim());
        }
        return credentialsBuilder.build();
    }

    @CacheEvict(value = "s3-clients", allEntries = true)
    @Scheduled(fixedRateString = "3600000")
    public void emptyS3ClientsCache() {
    }
}
