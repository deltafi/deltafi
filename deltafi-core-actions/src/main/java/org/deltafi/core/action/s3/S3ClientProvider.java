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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memoizes one {@link S3Client} per distinct {@link S3ClientParams}. An {@code S3Client} is
 * thread-safe and owns its own HTTP connection pool, so it is built once and shared across all
 * actions using the same configuration rather than recreated per request.
 *
 * <p>The map is bounded by {@link #MAX_CLIENTS} as a safety backstop (distinct configurations are
 * expected to be very few). On overflow an arbitrary entry's reference is dropped &mdash; never
 * closed &mdash; so a client that is still serving an in-flight request keeps working and is simply
 * rebuilt on its next use. All clients are closed on shutdown via {@link #close()}.
 */
@Component
@RequiredArgsConstructor
public class S3ClientProvider implements AutoCloseable {
    private static final int MAX_CLIENTS = 100;

    private final SslCredentials sslCredentials;
    private final ConcurrentHashMap<S3ClientParams, S3Client> clients = new ConcurrentHashMap<>();

    /**
     * Returns a shared {@link S3Client} for the given configuration, building one on first use.
     * The returned client is owned by this provider and reused across callers &mdash; callers must
     * <strong>not</strong> call {@link S3Client#close()} on it; doing so would break it for every
     * other action sharing the same configuration. Clients are closed only on shutdown.
     */
    public S3Client getS3Client(S3ClientParams params) {
        params.validate();

        if (params.isEnableSSL() && !sslCredentials.isConfigured()) {
            throw new IllegalStateException("SSL is not configured for this plugin and the S3Params has not disabled SSL");
        }

        S3Client client = clients.computeIfAbsent(params, this::buildS3Client);

        if (clients.size() > MAX_CLIENTS) {
            // Backstop eviction: drop an arbitrary entry's reference (never close it, so an in-flight
            // request is never torn down). The evicted client is rebuilt on its next use.
            clients.keySet().stream()
                    .filter(key -> !key.equals(params))
                    .findAny()
                    .ifPresent(clients::remove);
        }

        return client;
    }

    // package-private so tests can override it to return mocks rather than real AWS clients
    S3Client buildS3Client(S3ClientParams params) {
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

    @Override
    public void close() {
        clients.values().forEach(client -> {
            try {
                client.close();
            } catch (Exception e) {
                // best-effort shutdown; keep closing the rest
            }
        });
        clients.clear();
    }
}
