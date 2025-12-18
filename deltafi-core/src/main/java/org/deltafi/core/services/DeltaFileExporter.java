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
import org.deltafi.core.exceptions.ServerException;
import org.deltafi.core.generated.types.DeltaFileDirection;
import org.deltafi.core.generated.types.DeltaFileOrder;
import org.deltafi.core.generated.types.DeltaFilesFilter;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.types.DeltaFileDTO;
import org.deltafi.core.types.DeltaFiles;
import org.deltafi.core.types.ExportErrorsRequest;
import org.deltafi.core.util.DeltaFileMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeltaFileExporter {

    private final DeltaFileRepo deltaFileRepo;
    private final ContentStorageService contentStorageService;
    private final DeltaFilesService deltaFilesService;

    public StreamingResponseBody exportDeltaFile(UUID did) {
        DeltaFile deltaFile = deltaFileRepo.findById(did)
                .orElseThrow(() -> new EntityNotFound("Delta file with id " + did + " not found"));

        Export toExport = prepareExport(deltaFile);
        return outputStream -> pipeToTarFile(outputStream, List.of(toExport));
    }

    public StreamingResponseBody exportDeltaFiles(ExportErrorsRequest request) {
        DeltaFilesFilter errored = DeltaFilesFilter.newBuilder()
                .stage(DeltaFileStage.ERROR)
                .errorAcknowledged(false)
                .build();

        DeltaFileOrder oldestFirst = DeltaFileOrder.newBuilder()
                .direction(DeltaFileDirection.ASC).field("created").build();

        DeltaFiles deltaFiles = deltaFilesService.deltaFiles(0, request.getLimit(), errored, oldestFirst);
        if (deltaFiles.getDeltaFiles().isEmpty()) {
            throw new EntityNotFound("No DeltaFiles were found with unacknowledged errors");
        }

        List<UUID> dids = new ArrayList<>();
        List<Export> exports = new ArrayList<>();
        for (DeltaFile deltaFile : deltaFiles.getDeltaFiles()) {
            dids.add(deltaFile.getDid());
            exports.add(prepareExport(deltaFile));
        }

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

    private Export prepareExport(DeltaFile deltaFile) {
        DeltaFileDTO deltaFileDto = prepareDeltaFileDTO(deltaFile);
        return new Export(deltaFileDto, localizeContent(deltaFileDto));
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

    private Collection<Content> localizeContent(DeltaFileDTO deltaFile) {
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
                    throw new ServerException("Unable to determine size for segment " + segment.getUuid());
                }
                copy.setSize(size);
                content.setSegments(List.of(copy));
                segments.put(segment.getUuid(), content);
            }
        }

        deltaFile.setContentObjectIds(new ArrayList<>(segments.keySet()));
        return segments.values();
    }

    private record Export(DeltaFileDTO deltaFile, Collection<Content> content) {}
}
