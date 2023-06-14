/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.nifi.FlowFile;
import org.deltafi.common.nifi.FlowFileUtil;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.IngressEvent;
import org.deltafi.common.types.ProcessingType;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.*;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.metrics.MetricsUtil;
import org.deltafi.core.types.IngressResult;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;
import static org.deltafi.common.nifi.ContentType.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngressService {
    private final MetricService metricService;
    private final CoreAuditLogger coreAuditLogger;
    private final DiskSpaceService diskSpaceService;
    private final ContentStorageService contentStorageService;
    private final DeltaFilesService deltaFilesService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final FlowAssignmentService flowAssignmentService;
    private final IngressFlowService ingressFlowService;
    private final TransformFlowService transformFlowService;
    private final ErrorCountService errorCountService;
    private final UUIDGenerator uuidGenerator;

    public IngressResult ingress(String flow, String filename, String contentType, String username,
            String headerMetadataString, InputStream dataStream, OffsetDateTime created) throws IngressMetadataException,
            ObjectStorageException, IngressException, IngressStorageException, IngressUnavailableException {
        if (!deltaFiPropertiesService.getDeltaFiProperties().getIngress().isEnabled()) {
            log.error("Ingress error for flow={} filename={} contentType={} username={}: {}", flow, filename,
                    contentType, username, "Ingress disabled for this instance of DeltaFi");
            throw new IngressUnavailableException("Ingress disabled for this instance of DeltaFi");
        }

        if (diskSpaceService.isContentStorageDepleted()) {
            log.error("Ingress error for flow={} filename={} contentType={} username={}: {}", flow, filename,
                    contentType, username, "Ingress temporarily disabled due to storage limits");
            throw new IngressStorageException("Ingress temporarily disabled due to storage limits");
        }

        log.debug("Ingressing: flow={} filename={} contentType={} username={}", flow, filename, contentType, username);

        IngressResult ingressResult;
        try {
            Map<String, String> headerMetadata = parseMetadata(headerMetadataString);

            ingressResult = switch (contentType) {
                case APPLICATION_FLOWFILE, APPLICATION_FLOWFILE_V_1, APPLICATION_FLOWFILE_V_2,
                        APPLICATION_FLOWFILE_V_3 ->
                        ingressFlowFile(flow, filename, contentType, headerMetadata, dataStream, created);
                default -> ingressBinary(flow, filename, contentType, headerMetadata, dataStream, created);
            };
        } catch (IngressMetadataException | ObjectStorageException | IngressException e) {
            log.error("Ingress error for flow={} filename={} contentType={} username={}: {}", flow, filename,
                    contentType, username, e.getMessage());
            metricService.increment(DeltaFiConstants.FILES_DROPPED, tagsFor(flow), 1);
            throw e;
        }

        coreAuditLogger.logIngress(username, ingressResult.content().getName());

        Map<String, String> tags = tagsFor(ingressResult.flow());
        metricService.increment(DeltaFiConstants.FILES_IN, tags, 1);
        metricService.increment(DeltaFiConstants.BYTES_IN, tags, ingressResult.content().getSize());

        return ingressResult;
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

    private IngressResult ingressFlowFile(String flow, String filename, String contentType,
            Map<String, String> headerMetadata, InputStream contentInputStream, OffsetDateTime created)
            throws ObjectStorageException, IngressException, IngressMetadataException {
        FlowFile flowFile;
        try {
            flowFile = FlowFileUtil.unpackageFlowFile(contentType, contentInputStream);
        } catch (IOException e) {
            throw new IngressException("Unable to unpack FlowFile", e);
        }

        Map<String, String> combinedMetadata = new HashMap<>(flowFile.attributes());
        combinedMetadata.putAll(headerMetadata); // Metadata from header overrides attributes contained in FlowFile

        if (flow == null) {
            flow = combinedMetadata.get("flow");
        }
        if (filename == null) {
            filename = combinedMetadata.get("filename");
        }
        if (filename == null) {
            throw new IngressMetadataException("Filename must be passed in as a header or flowfile attribute");
        }
        return ingress(flow, filename, MediaType.APPLICATION_OCTET_STREAM, new ByteArrayInputStream(flowFile.content()),
                combinedMetadata, created);
    }

    private IngressResult ingress(String flow, String filename, String mediaType, InputStream contentInputStream,
            Map<String, String> metadata, OffsetDateTime created) throws ObjectStorageException, IngressException {
        SourceInfo sourceInfo = SourceInfo.builder()
                .flow(flow)
                .filename(filename)
                .metadata(metadata)
                .build();

        if ((sourceInfo.getFlow() == null) || sourceInfo.getFlow().equals(DeltaFiConstants.AUTO_RESOLVE_FLOW_NAME)) {
            String lookup = flowAssignmentService.findFlow(sourceInfo);
            if (lookup == null) {
                throw new IngressException("Unable to resolve flow based on current flow assignment rules");
            }
            sourceInfo.setFlow(lookup);
        }

        // ensure flow is running before accepting ingress
        if (ingressFlowService.hasRunningFlow(sourceInfo.getFlow())) {
            sourceInfo.setProcessingType(ProcessingType.NORMALIZATION);
        } else if (transformFlowService.hasRunningFlow(sourceInfo.getFlow())) {
            sourceInfo.setProcessingType(ProcessingType.TRANSFORMATION);
        } else {
            throw new IngressException("Flow " + sourceInfo.getFlow() + " is not running");
        }

        Integer maxErrors = ingressFlowService.maxErrorsPerFlow().get(sourceInfo.getFlow());
        if (maxErrors != null && maxErrors >= 0) {
            String error = errorCountService.generateErrorMessage(sourceInfo.getFlow(), maxErrors);
            if (error != null) {
                throw new IngressException(error);
            }
        }

        String did = uuidGenerator.generate();

        Content content = contentStorageService.save(did, contentInputStream, filename, mediaType);

        IngressEvent ingressEvent = IngressEvent.builder()
                .did(did)
                .sourceInfo(sourceInfo)
                .content(List.of(content))
                .created(created)
                .build();

        try {
            deltaFilesService.ingress(ingressEvent);
            return new IngressResult(sourceInfo.getFlow(), did, content, sourceInfo.getProcessingType());
        } catch (EnqueueActionException e) {
            log.warn("DeltaFile {} was ingressed but the next action could not be queued at this time", did);
            return new IngressResult(sourceInfo.getFlow(), did, content, sourceInfo.getProcessingType());
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
