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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.Segment;
import org.deltafi.common.test.content.InMemoryContentStorageService;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.exceptions.EntityNotFound;
import org.deltafi.core.exceptions.ServerException;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.types.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeltaFileExporterTest {
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    public static final String REFERENCED_DATA = "referenced data";
    public static final String OWNED_BY_THIS_DELTA_FILE = "owned by this DeltaFile";
    public static final String OWNED_BY_THIS_DELTA_FILE1 = "more content owned by this DeltaFile";
    public static final UUID DID = new UUID(0, 0);
    private UUID refContent;
    private UUID localContent;
    private UUID localContent2;

    private final DeltaFileRepo deltaFileRepo;
    private final ContentStorageService contentStorageService;
    private final DeltaFilesService deltaFilesService;
    private final DeltaFileExporter exporter;

    DeltaFileExporterTest() {
        this.deltaFileRepo = mock(DeltaFileRepo.class);
        this.contentStorageService = new InMemoryContentStorageService();
        this.deltaFilesService = mock(DeltaFilesService.class);
        exporter = new DeltaFileExporter(deltaFileRepo, contentStorageService, deltaFilesService);
    }

    @Test
    void export() throws IOException {
        // create a DeltaFile with 2 flows with a total of 4 content sections
        // One content has a segment that is owned by another DeltaFile
        // One content segment is used in multiple actions
        DeltaFile deltaFile = createDeltaFile(DID);
        when(deltaFileRepo.findById(DID)).thenReturn(Optional.of(deltaFile));

        StreamingResponseBody responseBody = exporter.exportDeltaFile(DID);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        responseBody.writeTo(outputStream);

        DeltaFileDTO exported;
        Map<String, String> contentMap = new HashMap<>();
        try (TarArchiveInputStream tarIn = new TarArchiveInputStream(
                new ByteArrayInputStream(outputStream.toByteArray()))) {

            tarIn.getNextEntry();
            exported = objectMapper.readValue(tarIn.readAllBytes(), DeltaFileDTO.class);

            TarArchiveEntry entry = tarIn.getNextEntry();
            while (entry != null) {
                contentMap.put(entry.getName(), new String(tarIn.readAllBytes(), StandardCharsets.UTF_8));
                entry = tarIn.getNextEntry();
            }
        }

        assertThat(exported).isNotNull();
        assertThat(exported.getParentDids()).isEmpty();
        assertThat(exported.getChildDids()).isEmpty();

        Set<UUID> segmentDids = deltaFile.getFlows().stream()
                .flatMap(f -> f.getActions().stream())
                .flatMap(a -> a.getContent().stream())
                .flatMap(c -> c.getSegments().stream())
                .map(Segment::getDid).collect(Collectors.toSet());

        assertThat(segmentDids).containsExactlyInAnyOrder(DID);

        // all the content paths should use this did regardless of what did the content originally belonged to
        String referencedContent = "content/" + Segment.objectName(DID, refContent);
        String localContentOne = "content/" + Segment.objectName(DID, localContent);
        String localContentTwo = "content/" + Segment.objectName(DID, localContent2);

        // there were 4 segments, but only 3 unique verify it was deduped
        assertThat(contentMap).hasSize(3)
                .containsEntry(referencedContent, REFERENCED_DATA)
                .containsEntry(localContentOne, OWNED_BY_THIS_DELTA_FILE)
                .containsEntry(localContentTwo, OWNED_BY_THIS_DELTA_FILE1);
    }


    @Test
    void exportDeltaFile_whenDeltaFileNotFound_throwsNotFoundException() {
        UUID did = UUID.randomUUID();
        when(deltaFileRepo.findById(did)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exporter.exportDeltaFile(did))
                .isInstanceOf(EntityNotFound.class)
                .hasMessageContaining("Delta file with id " + did + " not found");
    }


    @Test
    void exportDeltaFile_missingContent() {
        UUID did = UUID.randomUUID();
        DeltaFile deltaFile = createDeltaFile(did);
        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().stream().findFirst().orElseThrow();
        // change the pointer in segment so it can't be found when getting the size
        deltaFileFlow.getActions().getFirst().getContent().getFirst().getSegments().getFirst().setUuid(new UUID(0,4));

        when(deltaFileRepo.findById(did)).thenReturn(Optional.of(deltaFile));

        assertThatThrownBy(() ->  exporter.exportDeltaFile(did))
                .isInstanceOf(ServerException.class)
                .hasMessageContaining("Unable to determine size for segment 00000000-0000-0000-0000-000000000004");
    }

    @SneakyThrows
    private DeltaFile createDeltaFile(UUID did) {
        UUID parentId = UUID.randomUUID();
        DeltaFile deltaFile = new DeltaFile();
        deltaFile.setDid(did);
        deltaFile.setParentDids(List.of(parentId));
        deltaFile.setChildDids(List.of(UUID.randomUUID()));

        DeltaFileFlow flow = new DeltaFileFlow();
        flow.setFlowDefinition(new FlowDefinition(0, "flow", FlowType.TRANSFORM));
        Action action = new Action();
        Content referenced = contentStorageService.save(UUID.randomUUID(), REFERENCED_DATA.getBytes(StandardCharsets.UTF_8), "ref_content", "text/plain");
        refContent = referenced.getSegments().getFirst().getUuid();
        Content local = contentStorageService.save(DID, OWNED_BY_THIS_DELTA_FILE.getBytes(StandardCharsets.UTF_8), "local_content", "text/plain");
        localContent = local.getSegments().getFirst().getUuid();
        action.setContent(List.of(referenced, local));
        flow.getActions().add(action);
        deltaFile.getFlows().add(flow);

        DeltaFileFlow flow2 = new DeltaFileFlow();
        flow2.setFlowDefinition(new FlowDefinition(1, "flow", FlowType.TRANSFORM));
        Action action2 = new Action();

        Content moreLocal = contentStorageService.save(DID, OWNED_BY_THIS_DELTA_FILE1.getBytes(StandardCharsets.UTF_8), "local_content_2", "text/plain");
        localContent2 = moreLocal.getSegments().getFirst().getUuid();
        action2.setContent(List.of(local, moreLocal));
        flow2.getActions().add(action2);
        deltaFile.getFlows().add(flow2);

        return deltaFile;
    }
}