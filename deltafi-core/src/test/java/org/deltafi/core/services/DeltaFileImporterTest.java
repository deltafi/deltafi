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

import lombok.SneakyThrows;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.StorageProperties;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.test.content.InMemoryContentStorageService;
import org.deltafi.common.test.storage.s3.InMemoryObjectStorageService;
import org.deltafi.common.types.*;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.types.Action;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.types.DeltaFileFlow;
import org.deltafi.core.util.MockFlowDefinitionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DeltaFileImporterTest {

    private final DeltaFileRepo deltaFileRepo;
    private final ContentStorageService contentStorageService;
    private final DeltaFileImporter deltaFileImporter;
    @Captor
    private ArgumentCaptor<List<DeltaFile>> deltaFilesCaptor;

    DeltaFileImporterTest() {
        ObjectStorageService objectStorageService = new InMemoryObjectStorageService();
        deltaFileRepo = Mockito.mock(DeltaFileRepo.class);
        contentStorageService = new InMemoryContentStorageService(objectStorageService);
        deltaFileImporter = new DeltaFileImporter(deltaFileRepo, objectStorageService, new MockFlowDefinitionService(),
                new MockDeltaFiPropertiesService(), new StorageProperties("storage"));
    }

    @Test
    @SneakyThrows
    void importDeltaFile() {
        InputStream deltaFileTar = new ClassPathResource("deltafi-delta.tar").getInputStream();
        ImportResponse response = deltaFileImporter.importDeltaFile(deltaFileTar);
        assertThat(response.count()).isEqualTo(1);
        assertThat(response.bytes()).isEqualTo(676);
        Mockito.verify(deltaFileRepo).insertBatch(deltaFilesCaptor.capture(), Mockito.eq(1000));
        List<DeltaFile> deltaFiles = deltaFilesCaptor.getValue();

        assertThat(deltaFiles).hasSize(1);
        DeltaFile deltaFile = deltaFiles.getFirst();
        validateDeltaFile(deltaFile);

        DeltaFileFlow flow = deltaFile.getFlow(0);
        Content ingressContent = flow.getAction("IngressAction").getContent().getFirst();
        try (InputStream inputStream = contentStorageService.load(ingressContent)) {
            byte[] ingressInput = inputStream.readAllBytes();
            assertThat(ingressInput).hasSize((int)ingressContent.getSize());
        }

        Action decompressOutput = deltaFile.getFlow(1).getAction("Decompress");
        assertThat(readContent(decompressOutput.getContent().getFirst())).isEqualTo("three -\n");
        assertThat(readContent(decompressOutput.getContent().get(1))).isEqualTo("two -\n");
        assertThat(readContent(decompressOutput.getContent().getLast())).isEqualTo("one -\n");
    }

    @SneakyThrows
    private String readContent(Content content) {
        try (InputStream inputStream = contentStorageService.load(content)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void validateDeltaFile(DeltaFile deltaFile) {
        assertThat(deltaFile.getDid()).isEqualTo(UUID.fromString("968373c9-139e-4044-bb6b-6e11be5fdc7a"));
        assertThat(deltaFile.getName()).isEqualTo("my_files.zip");
        assertThat(deltaFile.getDataSource()).isEqualTo("passthrough-rest-data-source");
        assertThat(deltaFile.getParentDids()).isEmpty();
        assertThat(deltaFile.getJoinId()).isNull();
        assertThat(deltaFile.getChildDids()).isEmpty();
        assertThat(deltaFile.getRequeueCount()).isZero();
        assertThat(deltaFile.getIngressBytes()).isEqualTo(656L);
        assertThat(deltaFile.getReferencedBytes()).isEqualTo(676L);
        assertThat(deltaFile.getTotalBytes()).isEqualTo(676L);
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
        assertThat(deltaFile.isTerminal()).isTrue();
        assertThat(deltaFile.getEgressed()).isFalse();
        assertThat(deltaFile.getFiltered()).isFalse();
        assertThat(deltaFile.isWarnings()).isFalse();
        assertThat(deltaFile.isUserNotes()).isFalse();
        assertThat(deltaFile.isPinned()).isFalse();
        assertThat(deltaFile.getPaused()).isFalse();
        assertThat(deltaFile.getWaitingForChildren()).isFalse();
        assertThat(deltaFile.isContentDeletable()).isTrue();
        assertThat(deltaFile.getContentDeleted()).isNull();
        assertThat(deltaFile.getContentDeletedReason()).isNull();
        assertThat(deltaFile.getContentObjectIds()).containsExactlyInAnyOrder(
                UUID.fromString("f74e4040-7a89-4b0a-a2c4-103417a37dfb"),
                UUID.fromString("80e22d85-0ff9-441e-a568-42b206193015"),
                UUID.fromString("e121d225-4687-42e6-a89f-e5cff6d9d9f0"),
                UUID.fromString("f2cc54af-b45e-4648-9909-6b5aea9d8f15")
        );
        assertThat(deltaFile.getReplayed()).isNull();
        assertThat(deltaFile.getReplayDid()).isNull();
        assertThat(deltaFile.getAnnotations()).isEmpty();
        assertThat(deltaFile.getMessages()).isEmpty();
        assertThat(deltaFile.getTopics()).containsExactly("passthrough");
        assertThat(deltaFile.getTransforms()).containsExactly("decompressAndSplit");
        assertThat(deltaFile.getDataSinks()).isEmpty();
        assertThat(deltaFile.getCreated()).isEqualTo(OffsetDateTime.parse("2025-12-12T20:16:26.387Z"));
        assertThat(deltaFile.getModified()).isEqualTo(OffsetDateTime.parse("2025-12-12T20:16:35.302Z"));
        assertThat(deltaFile.getFlows()).hasSize(2);

        DeltaFileFlow dataSource = deltaFile.getFlow(0);
        assertThat(dataSource.getId()).isEqualTo(UUID.fromString("019b1435-3664-75a3-bc1d-60dd10e86452"));
        assertThat(dataSource.getFlowDefinition().getName()).isEqualTo("passthrough-rest-data-source");
        assertThat(dataSource.getFlowDefinition().getType()).isEqualTo(FlowType.REST_DATA_SOURCE);
        assertThat(dataSource.getState()).isEqualTo(DeltaFileFlowState.COMPLETE);
        assertThat(dataSource.getCreated()).isEqualTo(OffsetDateTime.parse("2025-12-12T20:16:26.387Z"));
        assertThat(dataSource.getModified()).isEqualTo(OffsetDateTime.parse("2025-12-12T20:16:30.308Z"));
        assertThat(dataSource.getPublishTopics()).containsExactly("passthrough");
        assertThat(dataSource.getDepth()).isZero();
        assertThat(dataSource.getPendingAnnotations()).isEmpty();
        assertThat(dataSource.isTestMode()).isFalse();
        assertThat(dataSource.getTestModeReason()).isNull();
        assertThat(dataSource.getJoinId()).isNull();
        assertThat(dataSource.getPendingActions()).isEmpty();
        assertThat(dataSource.getErrorAcknowledged()).isNull();
        assertThat(dataSource.getErrorAcknowledgedReason()).isNull();
        assertThat(dataSource.isColdQueued()).isFalse();
        assertThat(dataSource.getColdQueuedAction()).isNull();
        assertThat(dataSource.getErrorOrFilterCause()).isNull();
        assertThat(dataSource.getNextAutoResume()).isNull();
        assertThat(dataSource.getActions()).hasSize(1);
        Action ingressAction = dataSource.getActions().getFirst();
        assertThat(ingressAction.getName()).isEqualTo("IngressAction");
        assertThat(ingressAction.getType()).isEqualTo(ActionType.INGRESS);
        assertThat(ingressAction.getState()).isEqualTo(ActionState.COMPLETE);

        DeltaFileFlow transform = deltaFile.getFlow(1);
        assertThat(transform.getId()).isEqualTo(UUID.fromString("019b1435-3668-7898-9982-a3a9541d9594"));
        assertThat(transform.getFlowDefinition().getName()).isEqualTo("decompressAndSplit");
        assertThat(transform.getFlowDefinition().getType()).isEqualTo(FlowType.TRANSFORM);
        assertThat(transform.getState()).isEqualTo(DeltaFileFlowState.COMPLETE);
        assertThat(transform.getDepth()).isEqualTo(1);
        assertThat(transform.getPublishTopics()).isEmpty();

        // Flow 1 actions
        assertThat(transform.getActions()).hasSize(2);

        Action decompressAction = transform.getAction("Decompress");
        assertThat(decompressAction.getType()).isEqualTo(ActionType.TRANSFORM);
        assertThat(decompressAction.getState()).isEqualTo(ActionState.COMPLETE);
        assertThat(decompressAction.getActionClass()).isEqualTo("org.deltafi.core.action.compress.Decompress");
        assertThat(decompressAction.getContent()).hasSize(3);

        Action splitAction = transform.getAction("Split");
        assertThat(splitAction.getType()).isEqualTo(ActionType.TRANSFORM);
        assertThat(splitAction.getState()).isEqualTo(ActionState.SPLIT);
        assertThat(splitAction.getActionClass()).isEqualTo("org.deltafi.core.action.split.Split");
    }
}