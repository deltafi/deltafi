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
package org.deltafi.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.io.Writer;
import org.deltafi.common.io.WriterPipedInputStream;
import org.deltafi.common.nifi.FlowFileTwoStepUnpackager;
import org.deltafi.common.nifi.FlowFileTwoStepUnpackagerV1;
import org.deltafi.common.nifi.FlowFileTwoStepUnpackagerV2;
import org.deltafi.common.nifi.FlowFileTwoStepUnpackagerV3;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.IngressEventItem;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.*;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.metrics.MetricsUtil;
import org.deltafi.core.types.IngressResult;
import org.deltafi.core.types.RestDataSource;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
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
    public static final String INGRESS_ERROR_FOR_FLOW_FILENAME_CONTENT_TYPE_USERNAME = "Ingress error for flow={} filename={} contentType={} username={}: {}";
    private final MetricService metricService;
    private final CoreAuditLogger coreAuditLogger;
    private final DiskSpaceService diskSpaceService;
    private final ContentStorageService contentStorageService;
    private final DeltaFilesService deltaFilesService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final RestDataSourceService restDataSourceService;
    private final ErrorCountService errorCountService;
    private final UUIDGenerator uuidGenerator;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public List<IngressResult> ingress(String flow, String filename, String contentType, String username,
            String headerMetadataString, InputStream dataStream, OffsetDateTime created) throws IngressMetadataException,
            ObjectStorageException, IngressException, IngressStorageException, IngressUnavailableException,
            InterruptedException {
        if (!deltaFiPropertiesService.getDeltaFiProperties().isIngressEnabled()) {
            log.error(INGRESS_ERROR_FOR_FLOW_FILENAME_CONTENT_TYPE_USERNAME, flow, filename, contentType, username,
                    "Ingress disabled for this instance of DeltaFi");
            throw new IngressUnavailableException("Ingress disabled for this instance of DeltaFi");
        }

        if (diskSpaceService.isContentStorageDepleted()) {
            log.error(INGRESS_ERROR_FOR_FLOW_FILENAME_CONTENT_TYPE_USERNAME, flow, filename,
                    contentType, username, "Ingress temporarily disabled due to storage limits");
            throw new IngressStorageException("Ingress temporarily disabled due to storage limits");
        }

        log.debug("Ingressing: flow={} filename={} contentType={} username={}", flow, filename, contentType, username);

        List<IngressResult> ingressResults;
        try {
            Map<String, String> headerMetadata = parseMetadata(headerMetadataString);

            ingressResults = switch (contentType) {
                case APPLICATION_FLOWFILE, APPLICATION_FLOWFILE_V_1, APPLICATION_FLOWFILE_V_2,
                        APPLICATION_FLOWFILE_V_3 ->
                        ingressFlowFile(flow, filename, contentType, headerMetadata, dataStream, created);
                default -> List.of(ingressBinary(flow, filename, contentType, headerMetadata, dataStream, created));
            };
        } catch (IngressMetadataException | ObjectStorageException | IngressException e) {
            log.error(INGRESS_ERROR_FOR_FLOW_FILENAME_CONTENT_TYPE_USERNAME, flow, filename,
                    contentType, username, e.getMessage());
            metricService.increment(DeltaFiConstants.FILES_DROPPED, tagsFor(flow), 1);
            throw e;
        }

        ingressResults.forEach(ingressResult -> {
            coreAuditLogger.logIngress(username, ingressResult.content().getName());

            Map<String, String> tags = tagsFor(ingressResult.flow());
            metricService.increment(DeltaFiConstants.FILES_IN, tags, 1);
            metricService.increment(DeltaFiConstants.BYTES_IN, tags, ingressResult.content().getSize());
        });

        return ingressResults;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    Map<String, String> parseMetadata(String metadataString) throws IngressMetadataException {
        if (metadataString == null) {
            return Collections.emptyMap();
        }

        try {
            Map<String, JsonNode> keyValueMap = OBJECT_MAPPER.readValue(metadataString, new TypeReference<>() {});

            return keyValueMap.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            e -> e.getValue().isTextual() ? e.getValue().asText() : e.getValue().toString()));
        } catch (JsonProcessingException e) {
            throw new IngressMetadataException(
                    "Could not parse metadata, metadata must be a JSON Object: " + e.getMessage());
        }
    }

    private List<IngressResult> ingressFlowFile(String flow, String filename, String contentType,
            Map<String, String> headerMetadata, InputStream contentInputStream, OffsetDateTime created)
            throws ObjectStorageException, IngressException, IngressMetadataException {
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
                if (flow == null) {
                    flow = combinedMetadata.get("flow");
                }
                if (filename == null) {
                    filename = combinedMetadata.get("filename");
                }
                if (filename == null) {
                    throw new IngressMetadataException("Filename must be passed in as a header or FlowFile attribute");
                }

                Writer writer = outputStream -> flowFileTwoStepUnpackager.unpackageContent(contentInputStream, outputStream);
                try (WriterPipedInputStream writerPipedInputStream = WriterPipedInputStream.create(writer, executorService)) {
                    ingressResults.add(ingress(flow, filename, MediaType.APPLICATION_OCTET_STREAM,
                            writerPipedInputStream, combinedMetadata, created));
                }
            }
        } catch (IOException e) {
            throw new IngressException("Unable to unpack FlowFile", e);
        }

        return ingressResults;
    }

    private IngressResult ingress(String flow, String filename, String mediaType, InputStream contentInputStream,
            Map<String, String> metadata, OffsetDateTime created) throws ObjectStorageException, IngressException {
        RestDataSource restDataSource;
        try {
            restDataSource = restDataSourceService.getRunningFlowByName(flow);
        } catch (MissingFlowException e) {
            throw new IngressException(e.getMessage());
        }

        String error = errorCountService.generateErrorMessage(flow);
        if (error != null) {
            throw new IngressException(error);
        }

        UUID did = uuidGenerator.generate();

        Content content = contentStorageService.save(did, contentInputStream, filename, mediaType);

        IngressEventItem ingressEventItem = IngressEventItem.builder()
                .did(did)
                .deltaFileName(filename)
                .flowName(flow)
                .metadata(metadata)
                .content(List.of(content))
                .build();

        try {
            deltaFilesService.ingress(restDataSource, ingressEventItem, created, OffsetDateTime.now());
            return new IngressResult(flow, did, content);
        } catch (EnqueueActionException e) {
            log.warn("DeltaFile {} was ingressed but the next action could not be queued at this time", did);
            return new IngressResult(flow, did, content);
        } catch (Exception e) {
            log.warn("Ingress failed, removing content and metadata for {}", did);
            deltaFilesService.deleteContentAndMetadata(did, content);
            throw new IngressException("Ingress failed", e);
        }
    }

    private IngressResult ingressBinary(String flow, String filename, String mediaType,
            Map<String, String> headerMetadata, InputStream contentInputStream, OffsetDateTime created)
            throws IngressMetadataException, IngressException, ObjectStorageException {
        if (filename == null) {
            throw new IngressMetadataException("Filename must be passed in as a header");
        }
        return ingress(flow, filename, mediaType, contentInputStream, headerMetadata, created);
    }

    private Map<String, String> tagsFor(String ingressFlow) {
        return MetricsUtil.tagsFor(ActionType.INGRESS.name(), INGRESS_ACTION, ingressFlow, null);
    }
}
