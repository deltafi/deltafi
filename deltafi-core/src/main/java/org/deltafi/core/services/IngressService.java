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
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.*;
import org.deltafi.core.exceptions.IngressException;
import org.deltafi.core.exceptions.IngressMetadataException;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngressService {
    private final ContentStorageService contentStorageService;
    private final DeltaFilesService deltaFilesService;
    private final ObjectMapper objectMapper;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final FlowAssignmentService flowAssignmentService;
    private final IngressFlowService ingressFlowService;
    private final ErrorCountService errorCountService;

    @RequiredArgsConstructor
    @Data
    static public class IngressResult {
        public final ContentReference contentReference;
        public final String flow;
        public final String filename;
        public final String did;
    }

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
    public SourceInfo validateSourceInfo(SourceInfo sourceInfo) throws IngressException {
        if (sourceInfo.getFilename() == null) throw new IngressException("filename required in source info");

        if (sourceInfo.getFlow() == null) sourceInfo.setFlow(DeltaFiConstants.AUTO_RESOLVE_FLOW_NAME);

        if (DeltaFiConstants.AUTO_RESOLVE_FLOW_NAME.equals(sourceInfo.getFlow())) {
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

    public IngressResult ingressData(InputStream inputStream, String sourceFileName, String namespacedFlow, Map<String, String> metadata, String mediaType) throws ObjectStorageException, IngressException {

        SourceInfo sourceInfo = validateSourceInfo(
                SourceInfo.builder()
                        .filename(sourceFileName)
                        .flow(namespacedFlow)
                        .metadata(metadata)
                        .build()
        );

        String error = errorCountService.flowErrorsExceeded(sourceInfo.getFlow());
        if (error != null) {
            throw new IngressException(error);
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
            DeltaFile deltaFile = deltaFilesService.ingress(ingressInput);
            return new IngressResult(contentReference, deltaFile.getSourceInfo().getFlow(), sourceFileName, did);
        } catch (Exception e) {
            contentStorageService.delete(contentReference);
            throw e;
        }
    }

    public IngressResult ingressData(InputStream inputStream, String sourceFileName, String flow, String metadataString, String mediaType)
            throws ObjectStorageException, IngressException, IngressMetadataException {
        return ingressData(inputStream, sourceFileName, flow, fromMetadataString(metadataString), mediaType);
    }

    private String nodeValue(JsonNode node) {
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
