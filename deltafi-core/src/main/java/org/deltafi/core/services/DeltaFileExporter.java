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
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.DeltaFileStage;
import org.deltafi.core.exceptions.EntityNotFound;
import org.deltafi.core.exceptions.InvalidRequestException;
import org.deltafi.core.exceptions.ServerException;
import org.deltafi.core.generated.types.DeltaFileDirection;
import org.deltafi.core.generated.types.DeltaFileOrder;
import org.deltafi.core.generated.types.DeltaFilesFilter;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.types.*;
import org.deltafi.core.util.DeltaFileMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeltaFileExporter {

    private static final DataSize NO_SIZE_LIMIT = null;
    private static final DeltaFileOrder OLDEST_FIRST = DeltaFileOrder.newBuilder()
            .direction(DeltaFileDirection.ASC).field("created").build();

    private final DeltaFileRepo deltaFileRepo;
    private final ContentStorageService contentStorageService;
    private final DeltaFilesService deltaFilesService;

    public StreamingResponseBody exportDeltaFile(UUID did) {
        DeltaFile deltaFile = deltaFileRepo.findById(did)
                .orElseThrow(() -> new EntityNotFound("Delta file with id " + did + " not found"));

        Export toExport = prepareExport(deltaFile, true);
        return outputStream -> pipeToTarFile(outputStream, List.of(toExport));
    }

    public StreamingResponseBody exportDeltaFiles(ExportRequest exportRequest) {
        List<UUID> dids = new ArrayList<>();
        List<Export> exports = findForExport(exportRequest.getDeltaFilesFilter(), exportRequest.getOrder(),
                exportRequest.getOffset(), exportRequest.getLimit(), NO_SIZE_LIMIT, exportRequest.isIncludeContent(), dids);

        return outputStream -> {
            pipeToTarFile(outputStream, exports);
            outputStream.flush();
        };
    }

    public StreamingResponseBody exportDeltaFiles(ExportErrorsRequest request) {
        List<UUID> dids = new ArrayList<>();
        List<Export> exports = findForExport(request, dids);

        return outputStream -> {
            pipeToTarFile(outputStream, exports);
            outputStream.flush();

            if (request.isAcknowledge()) {
                String reason = StringUtils.isNotBlank(request.getReason()) ? request.getReason() :
                        "DeltaFile was exported for further evaluation";
                deltaFilesService.acknowledge(dids, reason);
            }
        };
    }

    List<Export> findForExport(ExportErrorsRequest request, List<UUID> dids) {
        DeltaFilesFilter errored = DeltaFilesFilter.newBuilder()
                .stage(DeltaFileStage.ERROR)
                .errorAcknowledged(false)
                .build();

        return findForExport(errored, OLDEST_FIRST, 0, request.getLimit(), request.getMaxSize(), true, dids);
    }

    List<Export> findForExport(DeltaFilesFilter filter, DeltaFileOrder order, int offset, int limit,
                               DataSize requestedMaxSize, boolean includeContent, List<UUID> dids) {
        DeltaFileOrder deltaFileOrder = order != null ? order : OLDEST_FIRST;
        DeltaFiles deltaFiles = deltaFilesService.deltaFiles(offset, limit, filter, deltaFileOrder);
        if (deltaFiles.getDeltaFiles().isEmpty()) {
            throw new EntityNotFound("No DeltaFiles were found for export");
        }

        List<Export> exports = new ArrayList<>();

        long maxSize = maxSize(requestedMaxSize);
        long currentSize = 0;
        for (DeltaFile deltaFile : deltaFiles.getDeltaFiles()) {
            Export export = prepareExport(deltaFile, includeContent);
            if (maxSize == -1 || exports.isEmpty() || currentSize + export.size() <= maxSize) {
                dids.add(deltaFile.getDid());
                exports.add(export);
                currentSize += export.size();
            } else {
                break;
            }
        }
        return exports;
    }

    private long maxSize(DataSize dataSize) {
        if  (dataSize == null) {
            return -1;
        }

        if (dataSize.toBytes() <= 0) {
            throw new InvalidRequestException("The max size value must be greater than zero");
        }

        return dataSize.toBytes();
    }

    private Export prepareExport(DeltaFile deltaFile, boolean includeContent) {
        try {
            DeltaFileDTO deltaFileDto = prepareDeltaFileDTO(deltaFile);
            Collection<Content> content = includeContent ? localizeContent(deltaFileDto) : List.of();
            return new Export(deltaFileDto, content);
        } catch (IOException e) {
            throw new InvalidRequestException("Could not prepare the DeltaFile (%s) for export".formatted(deltaFile.getDid()));
        }
    }

    // Convert to a DTO that can be cleanly exported/imported, unset anything that shouldn't be preserved
    private DeltaFileDTO prepareDeltaFileDTO(DeltaFile deltaFile) {
        DeltaFileDTO deltaFileDTO = DeltaFileDTO.from(deltaFile);
        deltaFileDTO.setChildDids(List.of());
        deltaFileDTO.setParentDids(List.of());
        return deltaFileDTO;
    }

    private void pipeToTarFile(OutputStream outputStream, List<Export> exports) {
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(outputStream)) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            for (Export export : exports) {
                DeltaFileDTO deltaFile =  export.deltaFile();
                Collection<Content> contents = export.content();
                byte[] jsonBytes = DeltaFileMapper.toJsonBytes(deltaFile);
                TarArchiveEntry deltaFileJson = new TarArchiveEntry(deltaFile.getDid() + ".json");
                deltaFileJson.setSize(jsonBytes.length);
                tarOut.putArchiveEntry(deltaFileJson);
                tarOut.write(jsonBytes);
                tarOut.closeArchiveEntry();

                for (Content content : contents) {
                    Segment segment = content.getSegments().getFirst();
                    TarArchiveEntry entry = new TarArchiveEntry("content/" + Segment.objectName(deltaFile.getDid(), segment.getUuid()));
                    entry.setSize(segment.getSize());

                    tarOut.putArchiveEntry(entry);
                    try (InputStream inputStream = contentStorageService.load(content)) {
                        inputStream.transferTo(tarOut);
                    }
                    tarOut.closeArchiveEntry();
                }
            }

            tarOut.finish();
        } catch (Exception e) {
            log.error("Error creating the DeltaFile tar", e);
            throw new ServerException("Error creating the DeltaFile tar");
        }
    }

    /**
     * Modify all the segments in this DeltaFile to appear as though they are owned by the DeltaFile that is being exported
     * @param deltaFile that is going to be exported
     * @return collection of content references whose content will be included in the tar export
     */
    private Collection<Content> localizeContent(DeltaFileDTO deltaFile) {
        if (deltaFile.getContentDeleted() != null) {
            // if this DeltaFile is marked as content deleted there is no need to localize the content in the DeltaFile metadata
            return List.of();
        }
        Map<UUID, Content> segments = new HashMap<>();
        // use all segments to ensure all segments repeated along multiple actions/flows get the updated did
        for (Segment segment : deltaFile.allSegments()) {
            // copy the original segment with the original did for content retrieval
            Segment copy = new Segment(segment);
            // rewrite the segment did so it will be owned by the exported DeltaFile when it is imported
            segment.setDid(deltaFile.getDid());
            if (!segments.containsKey(segment.getUuid())) {
                Content content = new Content();
                long size = contentStorageService.getTotalSize(copy);
                if (size < 0) {
                    UUID contentOwnerDid = copy.getDid();
                    if (contentOwnerDid.equals(deltaFile.getDid()) || contentNotDeleted(contentOwnerDid)) {
                        // the DeltaFile that created this content is not marked with contentDeleted, something went wrong getting the size
                        throw new ServerException("Unable to determine size for segment " + segment.getUuid() + " in deltaFile " + deltaFile.getDid());
                    } else {
                        // the content was removed, just keep the original size of the segment, no need for the overall size
                        size = copy.getSize();
                    }
                }
                copy.setOffset(0);
                copy.setSize(size);
                content.setSegments(List.of(copy));
                segments.put(segment.getUuid(), content);
            }
        }

        deltaFile.setContentObjectIds(new ArrayList<>(segments.keySet()));
        return segments.values();
    }

    private boolean contentNotDeleted(UUID did) {
        DeltaFile deltaFile = deltaFileRepo.findById(did).orElse(null);
        // if we can't find the DeltaFile assume the content was deleted too
        if (deltaFile == null) {
            return false;
        }
        return deltaFile.getContentDeleted() == null;
    }

    @SuppressWarnings("java:S6218")
    record Export(DeltaFileDTO deltaFile, Collection<Content> content, byte[] deltaFileJson) {
        public Export(DeltaFileDTO deltaFile, Collection<Content> content) throws IOException {
            this(deltaFile, content, DeltaFileMapper.toJsonBytes(deltaFile));
        }

        public long size() {
            long jsonSize = deltaFileJson != null ? deltaFileJson.length : 0;
            long contentSize = content != null ? content.stream().mapToLong(Content::getSize).sum() : 0;
            return jsonSize + contentSize;
        }
    }
}
