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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.input.BoundedInputStream;
import org.deltafi.common.content.StorageProperties;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.types.ImportResponse;
import org.deltafi.core.exceptions.InvalidImportException;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.types.DeltaFileDTO;
import org.deltafi.core.types.DeltaFileFlow;
import org.deltafi.core.types.DeltaFileFlowDTO;
import org.deltafi.core.util.DeltaFileMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeltaFileImporter {
    private static final String CONTENT_PREFIX = "content/";

    private final DeltaFileRepo deltaFileRepo;
    private final ObjectStorageService objectStorageService;
    private final FlowDefinitionService flowDefinitionService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final StorageProperties storageProperties;

    public ImportResponse importDeltaFile(InputStream inputStream) {
        List<DeltaFile> deltaFiles = new ArrayList<>();
        long contentBytes = 0;
        try (TarArchiveInputStream tarIn = new TarArchiveInputStream(inputStream)) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                if (!tarIn.canReadEntryData(entry)) {
                    log.warn("Cannot read tar entry: {}", entry.getName());
                    continue;
                }

                String entryName = entry.getName();
                if (entryName.endsWith(".json")) {
                    deltaFiles.add(mapToDeltaFile(entryName, tarIn.readAllBytes()));
                } else if (entryName.startsWith(CONTENT_PREFIX)) {
                    contentBytes += entry.getSize();
                    String objectName = entryName.substring(CONTENT_PREFIX.length());
                    streamContentToStorage(tarIn, entry, objectName);
                } else {
                    log.warn("Unexpected entry in tar archive: {}", entryName);
                }
            }

            deltaFileRepo.insertBatch(deltaFiles, deltaFiPropertiesService.getDeltaFiProperties().getInsertBatchSize());
            return new ImportResponse(deltaFiles.size(), contentBytes);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Failed to read tar archive: " + e.getMessage());
        }
    }

    private DeltaFile mapToDeltaFile(String name, byte[] data) throws IOException {
        try {
            DeltaFileDTO dto = DeltaFileMapper.readJsonBytes(data);
            DeltaFile entity = new DeltaFile();
            entity.setDid(dto.getDid());
            entity.setName(dto.getName());
            entity.setDataSource(dto.getDataSource());
            entity.setParentDids(dto.getParentDids());
            entity.setJoinId(dto.getJoinId());
            entity.setChildDids(dto.getChildDids());

            // use a helper method that handles mapping the flow name and type to the flow definition
            Set<DeltaFileFlow> flows = dto.getFlows() != null ? dto.getFlows().stream()
                    .map(this::toDeltaFileFlow).collect(Collectors.toSet()) : new HashSet<>();
            entity.setFlows(flows);

            entity.setRequeueCount(dto.getRequeueCount());
            entity.setIngressBytes(dto.getIngressBytes());
            entity.setReferencedBytes(dto.getReferencedBytes());
            entity.setTotalBytes(dto.getTotalBytes());
            entity.setStage(dto.getStage());
            if (dto.getAnnotations() != null) {
                entity.addAnnotations(dto.getAnnotations());
            }
            entity.setCreated(dto.getCreated());
            entity.setModified(dto.getModified());
            entity.setEgressed(dto.getEgressed());
            entity.setFiltered(dto.getFiltered());
            entity.setReplayed(dto.getReplayed());
            entity.setReplayDid(dto.getReplayDid());
            entity.setTerminal(dto.isTerminal());
            entity.setWarnings(dto.isWarnings());
            entity.setUserNotes(dto.isUserNotes());
            entity.setPinned(dto.isPinned());
            entity.setMessages(dto.getMessages());
            entity.setContentDeletable(dto.isContentDeletable());
            entity.setContentDeleted(dto.getContentDeleted());
            entity.setContentDeletedReason(dto.getContentDeletedReason());
            entity.setContentObjectIds(dto.getContentObjectIds());
            entity.setTopics(dto.getTopics());
            entity.setTransforms(dto.getTransforms());
            entity.setDataSinks(dto.getDataSinks());
            entity.setPaused (dto.getPaused());
            entity.setWaitingForChildren (dto.getWaitingForChildren());
            return entity;

        } catch (IOException e) {
            throw new InvalidImportException("Invalid DeltaFile json found in the archive at: " + name);
        }
    }

    private void streamContentToStorage(TarArchiveInputStream tarIn, TarArchiveEntry entry, String objectName) {
        try {
            // Use BoundedInputStream to limit reads to entry size without closing underlying stream
            BoundedInputStream boundedStream = BoundedInputStream.builder().setInputStream(tarIn).setMaxCount(entry.getSize()).get();
            ObjectReference objectReference = new ObjectReference();
            objectReference.setName(objectName);
            objectReference.setBucket(storageProperties.bucketName());
            objectReference.setOffset(0);
            objectReference.setSize(entry.getSize());
            objectStorageService.putObject(objectReference, boundedStream);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store content '" + objectName + "': " + e.getMessage());
        }
    }

    private DeltaFileFlow toDeltaFileFlow(DeltaFileFlowDTO flow) {
        return DeltaFileFlow.builder()
                .id(flow.getId())
                .flowDefinition(flowDefinitionService.getOrCreateFlow(flow.getName(), flow.getType()))
                .number(flow.getNumber())
                .state(flow.getState())
                .created(flow.getCreated())
                .modified(flow.getModified())
                .input(flow.getInput())
                .actions(flow.getActions())
                .publishTopics(flow.getPublishTopics())
                .depth(flow.getDepth())
                .pendingAnnotations(flow.getPendingAnnotations())
                .testMode(flow.isTestMode())
                .testModeReason(flow.getTestModeReason())
                .joinId(flow.getJoinId())
                .pendingActions(flow.getPendingActions())
                .errorAcknowledged(flow.getErrorAcknowledged())
                .errorAcknowledgedReason(flow.getErrorAcknowledgedReason())
                .coldQueued(flow.isColdQueued())
                .coldQueuedAction(flow.getColdQueuedAction())
                .errorOrFilterCause(flow.getErrorOrFilterCause())
                .nextAutoResume(flow.getNextAutoResume())
                .build();
    }
}
