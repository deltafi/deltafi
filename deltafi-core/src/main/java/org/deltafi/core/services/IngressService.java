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
package org.deltafi.core.services;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.io.Writer;
import org.deltafi.common.io.WriterPipedInputStream;
import org.deltafi.common.nifi.*;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.*;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.core.exceptions.*;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.metrics.MetricsUtil;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.types.*;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;
import static org.deltafi.common.nifi.ContentType.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngressService {
    public static final String INGRESS_ERROR_FOR_DATASOURCE_FILENAME_CONTENT_TYPE_USERNAME = "Ingress error for dataSource={} filename={} contentType={} username={}: {}";
    private final MetricService metricService;
    private final SystemService systemService;
    private final ContentStorageService contentStorageService;
    private final DeltaFilesService deltaFilesService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final RestDataSourceService restDataSourceService;
    private final ErrorCountService errorCountService;
    private final UUIDGenerator uuidGenerator;
    private final AnalyticEventService analyticEventService;
    private final RateLimitService rateLimitService;

    private final ExecutorService executorService = Executors.newCachedThreadPool();


    public List<IngressResult> ingress(String dataSource, String filename, String contentType, String username,
            String headerMetadataString, InputStream dataStream, OffsetDateTime created) throws IngressMetadataException,
            ObjectStorageException, IngressException, IngressStorageException, IngressUnavailableException, IngressRateLimitException {
        ingressPreCheck(username, dataSource, filename, contentType);

        List<IngressResult> ingressResults;
        try {
            Map<String, String> headerMetadata = parseMetadata(headerMetadataString);

            ingressResults = switch (contentType) {
                case APPLICATION_FLOWFILE, APPLICATION_FLOWFILE_V_1, APPLICATION_FLOWFILE_V_2,
                        APPLICATION_FLOWFILE_V_3 ->
                        ingressFlowFile(dataSource, filename, contentType, headerMetadata, dataStream, created);
                default -> List.of(ingressBinary(dataSource, filename, contentType, headerMetadata, dataStream, created));
            };
        } catch (IngressMetadataException | ObjectStorageException | IngressException | IngressUnavailableException | IngressRateLimitException e) {
            log.error(INGRESS_ERROR_FOR_DATASOURCE_FILENAME_CONTENT_TYPE_USERNAME, dataSource, filename,
                    contentType, username, e.getMessage());
            metricService.increment(DeltaFiConstants.FILES_DROPPED, tagsFor(dataSource), 1);
            throw e;
        }

        ingressResults.forEach(ingressResult -> analyticEventService.recordIngress(ingressResult.did(),
                created, ingressResult.dataSource(), FlowType.REST_DATA_SOURCE, ingressResult.content().getFirst().getSize(),
                Collections.emptyMap(), AnalyticIngressTypeEnum.DATA_SOURCE));

        return ingressResults;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    Map<String, String> parseMetadata(String metadataString) throws IngressMetadataException {
        if (metadataString == null || metadataString.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            Map<String, JsonNode> keyValueMap = OBJECT_MAPPER.readValue(metadataString, new TypeReference<>() {});

            return keyValueMap.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            e -> e.getValue().isString() ? e.getValue().asString() : e.getValue().toString()));
        } catch (JacksonException e) {
            throw new IngressMetadataException(
                    "Could not parse metadata, metadata must be a JSON Object: " + e.getMessage());
        }
    }

    private List<IngressResult> ingressFlowFile(String dataSource, String filename, String contentType,
            Map<String, String> headerMetadata, InputStream contentInputStream, OffsetDateTime created)
            throws ObjectStorageException, IngressException, IngressMetadataException, IngressUnavailableException, IngressRateLimitException {
        FlowFileTwoStepUnpackager flowFileTwoStepUnpackager = switch (contentType) {
            case APPLICATION_FLOWFILE, APPLICATION_FLOWFILE_V_1 -> new FlowFileTwoStepUnpackagerV1();
            case APPLICATION_FLOWFILE_V_2 -> new FlowFileTwoStepUnpackagerV2();
            case APPLICATION_FLOWFILE_V_3 -> new FlowFileTwoStepUnpackagerV3();
            default -> throw new IllegalStateException("Unexpected value: " + contentType);
        };

        List<IngressResult> ingressResults = new ArrayList<>();

        try {
            while (flowFileTwoStepUnpackager.hasMoreData()) {
                Map<String, String> combinedMetadata =
                        new HashMap<>(flowFileTwoStepUnpackager.unpackageAttributes(contentInputStream));
                combinedMetadata.putAll(headerMetadata); // Metadata from header overrides attributes contained in FlowFile
                if (dataSource == null) {
                    dataSource = combinedMetadata.get("dataSource");
                }
                if (filename == null) {
                    filename = combinedMetadata.get("filename");
                }
                if (filename == null) {
                    throw new IngressMetadataException("Filename must be passed in as a header, a FlowFile attribute, or in the metadata with a key of filename");
                }
                if (dataSource == null) {
                    throw new IngressMetadataException("DataSource must be passed in as a header, a FlowFile attribute, or in the metadata with a key of dataSource");
                }

                Writer writer = outputStream -> flowFileTwoStepUnpackager.unpackageContent(contentInputStream, outputStream);
                try (WriterPipedInputStream writerPipedInputStream = WriterPipedInputStream.create(writer, executorService)) {
                    ingressResults.add(ingress(dataSource, filename, MediaType.APPLICATION_OCTET_STREAM,
                            writerPipedInputStream, combinedMetadata, created));
                }
            }
        } catch (IOException e) {
            throw new IngressException("Unable to unpack FlowFile", e);
        }

        return ingressResults;
    }

    private IngressResult ingress(String dataSource, String filename, String mediaType, InputStream contentInputStream,
            Map<String, String> metadata, OffsetDateTime created) throws ObjectStorageException, IngressException, IngressMetadataException, IngressRateLimitException {
        RestDataSource restDataSource;
        try {
            restDataSource = restDataSourceService.getActiveFlowByName(dataSource);
        } catch (MissingFlowException e) {
            throw new IngressMetadataException(e.getMessage());
        }

        if (restDataSource.getRateLimit() != null) {
            var rateLimit = restDataSource.getRateLimit();

            if (rateLimit.getUnit() == org.deltafi.core.generated.types.RateLimitUnit.FILES) {
                // FILES: try to consume 1 file token
                if (!rateLimitService.tryConsume(dataSource, 1, rateLimit.getMaxAmount(), Duration.ofSeconds(rateLimit.getDurationSeconds()))) {
                    log.warn("Rate limit exceeded for data source '{}' - rejecting ingress for filename={}", dataSource, filename);
                    throw new IngressRateLimitException("Rate limit exceeded - " + dataSource + " allows " +
                        rateLimit.getMaxAmount() + " files per " + rateLimit.getDurationSeconds() + " seconds");
                }
            } else { // BYTES
                // BYTES: check if there's room for at least 1 byte
                if (!rateLimitService.tryConsume(dataSource, 1, rateLimit.getMaxAmount(), Duration.ofSeconds(rateLimit.getDurationSeconds()))) {
                    log.warn("Rate limit exceeded for data source '{}' - rejecting ingress for filename={}", dataSource, filename);
                    throw new IngressRateLimitException("Rate limit exceeded - " + dataSource + " allows " +
                        rateLimit.getMaxAmount() + " bytes per " + rateLimit.getDurationSeconds() + " seconds");
                }
            }
        }

        errorCountService.checkErrorsExceeded(FlowType.REST_DATA_SOURCE, dataSource);

        UUID did = uuidGenerator.generate();

        Content content = contentStorageService.save(did, contentInputStream, filename, mediaType);

        // For BYTES rate limiting: consume the actual byte count (minus the 1 byte we already consumed)
        if (restDataSource.getRateLimit() != null &&
            restDataSource.getRateLimit().getUnit() == org.deltafi.core.generated.types.RateLimitUnit.BYTES) {
            var rateLimit = restDataSource.getRateLimit();
            long remainingBytes = content.getSize() - 1; // We already consumed 1 byte above
            if (remainingBytes > 0) {
                rateLimitService.consume(dataSource, remainingBytes, rateLimit.getMaxAmount(), Duration.ofSeconds(rateLimit.getDurationSeconds()));
            }
            log.debug("Consumed {} bytes total from rate limit bucket for data source '{}'", content.getSize(), dataSource);
        }

        IngressEventItem ingressEventItem = IngressEventItem.builder()
                .did(did)
                .deltaFileName(filename)
                .flowName(dataSource)
                .metadata(metadata)
                .content(List.of(content))
                .build();

        return executeIngress(restDataSource, ingressEventItem, created);
    }

    public IngressResult ingress(IngressEventItem ingressEventItem, String username) throws IngressMetadataException, IngressException, IngressUnavailableException, IngressStorageException {
        String mediaType = MediaType.MULTIPART_FORM_DATA;
        if (ingressEventItem.getContent() == null || ingressEventItem.getContent().isEmpty()) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        } else if (ingressEventItem.getContent().size() == 1) {
            mediaType = ingressEventItem.getContent().getFirst().getMediaType();
        }

        ingressPreCheck(username, ingressEventItem.getFlowName(), ingressEventItem.getDeltaFileName(), mediaType);

        RestDataSource restDataSource;
        try {
            restDataSource = restDataSourceService.getActiveFlowByName(ingressEventItem.getFlowName());
        } catch (MissingFlowException e) {
            throw new IngressMetadataException(e.getMessage());
        }

        return executeIngress(restDataSource, ingressEventItem, OffsetDateTime.now());
    }

    private void ingressPreCheck(String username, String dataSource, String filename, String contentType) throws IngressStorageException, IngressUnavailableException {
        username = StringUtils.isNotBlank(username) ? username : "system";
        if (!deltaFiPropertiesService.getDeltaFiProperties().isIngressEnabled()) {
            log.error(INGRESS_ERROR_FOR_DATASOURCE_FILENAME_CONTENT_TYPE_USERNAME, dataSource, filename, contentType, username,
                    "Ingress disabled for this instance of DeltaFi");
            throw new IngressUnavailableException("Ingress disabled for this instance of DeltaFi");
        }

        if (systemService.isContentStorageDepleted()) {
            log.error(INGRESS_ERROR_FOR_DATASOURCE_FILENAME_CONTENT_TYPE_USERNAME, dataSource, filename,
                    contentType, username, "Ingress temporarily disabled due to storage limits");
            throw new IngressStorageException("Ingress temporarily disabled due to storage limits");
        }

        log.debug("Ingressing: dataSource={} filename={} contentType={} username={}", dataSource, filename, contentType, username);
    }

    private IngressResult executeIngress(RestDataSource restDataSource, IngressEventItem ingressEventItem, OffsetDateTime created) throws IngressException {
        UUID did = ingressEventItem.getDid();
        try {
            deltaFilesService.ingressRest(restDataSource, ingressEventItem,  created, OffsetDateTime.now());
            return new IngressResult(ingressEventItem);
        } catch (EnqueueActionException e) {
            log.warn("DeltaFile {} was ingressed but the next action could not be queued at this time", did);
            return new IngressResult(ingressEventItem);
        } catch (Exception e) {
            log.warn("Ingress failed, removing content and metadata for {}", did);
            deltaFilesService.deleteContentAndMetadata(did, ingressEventItem.getContent());
            throw new IngressException("Ingress failed", e);
        }
    }

    private IngressResult ingressBinary(String dataSource, String filename, String mediaType,
            Map<String, String> headerMetadata, InputStream contentInputStream, OffsetDateTime created)
            throws IngressMetadataException, IngressException, IngressUnavailableException, IngressRateLimitException, ObjectStorageException {
        if (dataSource == null) {
            dataSource = headerMetadata.get("dataSource");
        }
        if (filename == null) {
            filename = headerMetadata.get("filename");
        }

        if (filename == null) {
            throw new IngressMetadataException("Filename must be passed in as a header or in the metadata with a key of filename");
        }
        if (dataSource == null) {
            throw new IngressMetadataException("DataSource must be passed in as a header or in the metadata with a key of dataSource");
        }
        return ingress(dataSource, filename, mediaType, contentInputStream, headerMetadata, created);
    }

    private Map<String, String> tagsFor(String ingressDataSource) {
        return MetricsUtil.tagsFor(ActionType.INGRESS.name(), INGRESS_ACTION, ingressDataSource, null);
    }
}
