/**
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
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.IngressEvent;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.core.exceptions.EnqueueActionException;
import org.deltafi.core.exceptions.IngressException;
import org.deltafi.core.exceptions.IngressMetadataException;
import org.deltafi.core.nifi.FlowFile;
import org.deltafi.core.nifi.FlowFileUtil;
import org.deltafi.core.types.IngressResult;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngressService {

    public static final String FLOWFILE_MEDIA_TYPE = "application/flowfile";
    public static final String FLOWFILE_V1_MEDIA_TYPE = "application/flowfile-v1";

    private final ContentStorageService contentStorageService;
    private final DeltaFilesService deltaFilesService;
    private final ObjectMapper objectMapper;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final FlowAssignmentService flowAssignmentService;
    private final IngressFlowService ingressFlowService;
    private final ErrorCountService errorCountService;

    /**
     * Check to see if the ingress is globally enabled
     * @return true if ingress is enabled, false otherwise
     */
    public boolean isEnabled() {
        return deltaFiPropertiesService.getDeltaFiProperties().getIngress().isEnabled();
    }

    /**
     * Evaluate source info validity:
     * <ul>
     *     <li>Check if filename is present</li>
     *     <li>Check if flow is present or resolvable</li>
     *     <li>Check if flow is running</li>
     * </ul>
     * @param sourceInfo source info to validate
     * @return Source info with resolved flow (if necessary)
     * @throws IngressException if source info is malformed, unresolvable, or for an inactive flow
     */
    private SourceInfo validateSourceInfo(SourceInfo sourceInfo) throws IngressException {
        if (sourceInfo.getFilename() == null) throw new IngressException("filename required in source info");

        if (flowIsNullOrAutoResolve(sourceInfo.getFlow())) {
            String lookup = flowAssignmentService.findFlow(sourceInfo);
            if (lookup == null) throw new IngressException("Unable to resolve flow based on current flow assignment rules");
            sourceInfo.setFlow(lookup);
        }

        // ensure flow is running before accepting ingress
        try {
            ingressFlowService.getRunningFlowByName(sourceInfo.getFlow());
        } catch (DgsEntityNotFoundException e) {
            throw new IngressException("Flow " + sourceInfo.getFlow() + "is not running", e);
        }

        return sourceInfo;
    }

    public IngressResult ingressData(InputStream dataStream, String filename, String flow, String metadataString, String mediaType) throws IngressMetadataException, ObjectStorageException, IngressException {
        Map<String, String> metadata = fromMetadataString(metadataString);
        return switch (mediaType) {
            case FLOWFILE_MEDIA_TYPE, FLOWFILE_V1_MEDIA_TYPE -> ingressFlowfileV1(dataStream, filename, flow, metadata);
            default -> ingressBinary(dataStream, filename, flow, metadata, mediaType);
        };
    }

    private IngressResult ingressBinary(InputStream dataStream, String filename, String flow, Map<String, String> metadata, String mediaType) throws IngressMetadataException, IngressException, ObjectStorageException {
        if (filename == null) {
            throw new IngressMetadataException("Filename must be passed in as a header");
        }

        return ingressData(dataStream, filename, flow, metadata, mediaType);
    }

    private IngressResult ingressFlowfileV1(InputStream dataStream, String filename, String flow, Map<String, String> metadata) throws IngressMetadataException, IngressException, ObjectStorageException {
        FlowFile flowfile = FlowFileUtil.unarchiveFlowfileV1(dataStream, metadata);
        if (flow == null) {
            flow = flowfile.metadata().get("flow");
        }

        if (filename == null) {
            filename = flowfile.metadata().get("filename");
        }

        if (filename == null) {
            throw new IngressMetadataException("Filename must be passed in as a header or flowfile attribute");
        }

        return ingressData(new ByteArrayInputStream(flowfile.content()), filename, flow, flowfile.metadata(), MediaType.APPLICATION_OCTET_STREAM);
    }

    private IngressResult ingressData(InputStream inputStream, String sourceFileName, String flow, Map<String, String> metadata, String mediaType) throws ObjectStorageException, IngressException {
        SourceInfo sourceInfo = validateSourceInfo(
                SourceInfo.builder()
                        .filename(sourceFileName)
                        .flow(flow)
                        .metadata(metadata)
                        .build()
        );

        Integer maxErrors = ingressFlowService.maxErrorsPerFlow().get(sourceInfo.getFlow());
        if (maxErrors != null && maxErrors >= 0) {
            String error = errorCountService.generateErrorMessage(sourceInfo.getFlow(), maxErrors);
            if (error != null) {
                throw new IngressException(error);
            }
        }

        String did = UUID.randomUUID().toString();
        OffsetDateTime created = OffsetDateTime.now();

        ContentReference contentReference = contentStorageService.save(did, inputStream, mediaType);
        List<Content> content = Collections.singletonList(Content.newBuilder().contentReference(contentReference).name(sourceFileName).build());

        IngressEvent ingressInput = IngressEvent.newBuilder()
                .did(did)
                .sourceInfo(sourceInfo)
                .content(content)
                .created(created)
                .build();

        try {
            deltaFilesService.ingress(ingressInput);
            return new IngressResult(contentReference, sourceInfo.getFlow(), sourceFileName, did);
        } catch (EnqueueActionException e) {
            log.warn("DeltaFile {} was ingressed but the next action could not be queued at this time", did);
            return new IngressResult(contentReference, sourceInfo.getFlow(), sourceFileName, did);
        } catch (Exception e) {
            log.warn("Ingress failed, removing content and metadata for {}", did);
            deltaFilesService.deleteContentAndMetadata(did, contentReference);
            throw e;
        }
    }

    private static boolean flowIsNullOrAutoResolve(String flow) {
        return flow == null || DeltaFiConstants.AUTO_RESOLVE_FLOW_NAME.equals(flow);
    }

    private static String nodeValue(JsonNode node) {
        return node.isTextual() ? node.asText() : node.toString();
    }

    Map<String, String> fromMetadataString(String metadata) throws IngressMetadataException {
        if (Objects.isNull(metadata)) {
            return Collections.emptyMap();
        }

        try {
            Map<String, JsonNode> keyValueMap = objectMapper.readValue(metadata, new TypeReference<>() {});

            return keyValueMap.entrySet().
                    stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> nodeValue(e.getValue())
                    ));
        } catch (JsonProcessingException e) {
            throw new IngressMetadataException("Could not parse metadata, metadata must be a JSON Object: " + e.getMessage());
        }
    }
}
