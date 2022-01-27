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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final DiskSpaceService diskSpaceService;

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
     * Check to see if ingress storage is available based on the content bytes remaining and
     * the configured disk space requirement in system properties.
     * @return true if space is available, false otherwise
     */
    @Cacheable(cacheNames = {"diskspaceservice-storage"})
    public boolean isStorageAvailable() {
        return diskSpaceService.contentMetrics().bytesRemaining() > deltaFiPropertiesService.getDeltaFiProperties().getIngress().getDiskSpaceRequirementInMb() * 1000000;
    }

    /**
     * Cache eviction method for the disk space service call.  Flushed every 5 seconds.
     * This is scheduled and should not be called directly.
     */
    @CacheEvict(cacheNames = {"diskspaceservice-storage"})
    @Scheduled(fixedDelay = 5000)
    public void evictStorageAvailable() {
    }

    public IngressResult ingressData(InputStream inputStream, String sourceFileName, String namespacedFlow, Map<String, String> metadata, String mediaType) throws ObjectStorageException, IngressException {
        String flow = (namespacedFlow == null) ? DeltaFiConstants.AUTO_RESOLVE_FLOW_NAME : namespacedFlow;

        if (sourceFileName == null) throw new IngressException("filename required in source info");

        String did = UUID.randomUUID().toString();
        OffsetDateTime created = OffsetDateTime.now();

        ContentReference contentReference = contentStorageService.save(did, inputStream, mediaType);
        List<Content> content = Collections.singletonList(Content.newBuilder().contentReference(contentReference).name(sourceFileName).build());

        IngressEvent ingressInput = IngressEvent.newBuilder()
                .did(did)
                .sourceInfo(new SourceInfo(sourceFileName, flow, metadata))
                .content(content)
                .created(created)
                .build();

        try {
            DeltaFile deltaFile = deltaFilesService.ingress(ingressInput);
            flow = deltaFile.getSourceInfo().getFlow();
        } catch (Exception e) {
            contentStorageService.delete(contentReference);
            throw e;
        }

        return new IngressResult(contentReference, flow, sourceFileName, did);
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