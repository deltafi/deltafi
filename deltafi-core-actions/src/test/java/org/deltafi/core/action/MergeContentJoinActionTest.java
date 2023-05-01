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
package org.deltafi.core.action;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.deltafi.actionkit.action.join.JoinInput;
import org.deltafi.actionkit.action.join.JoinReinjectResult;
import org.deltafi.actionkit.action.join.JoinResult;
import org.deltafi.actionkit.action.join.JoinResultType;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.test.storage.s3.InMemoryObjectStorageService;
import org.deltafi.common.types.*;
import org.deltafi.core.parameters.ArchiveType;
import org.deltafi.core.parameters.MergeContentJoinParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class MergeContentJoinActionTest {
    private static final ContentStorageService CONTENT_STORAGE_SERVICE =
            new ContentStorageService(new InMemoryObjectStorageService());
    private static final MergeContentJoinAction MERGE_CONTENT_JOIN_ACTION = new MergeContentJoinAction();

    private static final String JOINED_DID = "12345";

    private static final ActionContext CONTEXT = ActionContext.builder().name("action").did(JOINED_DID).contentStorageService(CONTENT_STORAGE_SERVICE).build();
    private static List<JoinInput> joinInputs;

    @BeforeAll
    public static void beforeAll() throws ObjectStorageException {
        MERGE_CONTENT_JOIN_ACTION.setContentStorageService(CONTENT_STORAGE_SERVICE);

        ContentReference join1ContentReference = CONTENT_STORAGE_SERVICE.save("join-1-did",
                "join 1 content".getBytes(StandardCharsets.UTF_8), "join 1 media type");
        JoinInput join1 = JoinInput.builder()
                .contentList((List.of(Content.newBuilder()
                        .contentReference(join1ContentReference)
                        .name("join-1-file")
                        .build())
                )).build();

        ContentReference join2ContentReference = CONTENT_STORAGE_SERVICE.save("join-2-did",
                "join 2 content".getBytes(StandardCharsets.UTF_8), "join 2 media type");
        JoinInput join2 = JoinInput.builder()
                .contentList((List.of(Content.newBuilder()
                        .contentReference(join2ContentReference)
                        .name("join-2-file")
                        .build())
                )).build();

        joinInputs = List.of(join1, join2);
    }

    @Test
    public void mergesToBinaryConcatenation() throws ObjectStorageException, IOException {
        JoinResultType joinResult = MERGE_CONTENT_JOIN_ACTION.join(CONTEXT, new MergeContentJoinParameters(), joinInputs);

        assertInstanceOf(JoinResult.class, joinResult);
        try (InputStream joinedContentInputStream = CONTENT_STORAGE_SERVICE.load(
                ((JoinResult) joinResult).getContent().get(0).getContentReference())) {
            assertArrayEquals("join 1 contentjoin 2 content".getBytes(StandardCharsets.UTF_8),
                    joinedContentInputStream.readAllBytes());
        }
    }

    @Test
    public void mergesToTar() throws ObjectStorageException, IOException {
        execute(ArchiveType.TAR, Function.identity(), TarArchiveInputStream::new);
    }

    @Test
    public void mergesToZip() throws ObjectStorageException, IOException {
        execute(ArchiveType.ZIP, Function.identity(), ZipArchiveInputStream::new);
    }

    @Test
    public void mergesToAr() throws ObjectStorageException, IOException {
        execute(ArchiveType.AR, Function.identity(), ArArchiveInputStream::new);
    }

    @Test
    public void mergesToTarXz() throws ObjectStorageException, IOException {
        execute(ArchiveType.TAR_XZ, inputStream -> {
            try {
                return new XZCompressorInputStream(inputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, TarArchiveInputStream::new);
    }

    @Test
    public void mergesToTarGz() throws ObjectStorageException, IOException {
        execute(ArchiveType.TAR_GZIP, inputStream -> {
            try {
                return new GzipCompressorInputStream(inputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, TarArchiveInputStream::new);
    }

    @Test
    public void mergesAndReinjects() {
        MergeContentJoinParameters params = new MergeContentJoinParameters();
        params.setReinjectFlow("new-flow");

        JoinResultType joinResult = MERGE_CONTENT_JOIN_ACTION.join(CONTEXT, params, joinInputs);

        assertInstanceOf(JoinReinjectResult.class, joinResult);
        assertEquals("new-flow", ((JoinReinjectResult) joinResult).getFlow());
    }

    private void execute(ArchiveType archiveType, Function<InputStream, InputStream> joinedContentInputStreamSupplier,
            Function<InputStream, ArchiveInputStream> archiveInputStreamSupplier)
            throws IOException, ObjectStorageException {
        MergeContentJoinParameters params = new MergeContentJoinParameters();
        params.setArchiveType(archiveType);

        JoinResultType joinResult = MERGE_CONTENT_JOIN_ACTION.join(CONTEXT, params, joinInputs);

        assertInstanceOf(JoinResult.class, joinResult);
        assertEquals(archiveType.getMediaType(),
                ((JoinResult) joinResult).getContent().get(0).getContentReference().getMediaType());
        try (InputStream joinedContentInputStream = CONTENT_STORAGE_SERVICE.load(
                ((JoinResult) joinResult).getContent().get(0).getContentReference())) {
            validate(joinedContentInputStreamSupplier.apply(joinedContentInputStream), archiveInputStreamSupplier);
        }
    }

    private void validate(InputStream inputStream, Function<InputStream, ArchiveInputStream> archiveInputStreamSupplier)
            throws IOException {
        ArchiveInputStream archiveInputStream = archiveInputStreamSupplier.apply(inputStream);
        ArchiveEntry archiveEntry = archiveInputStream.getNextEntry();
        assertEquals("join-1-file", archiveEntry.getName());
        archiveEntry = archiveInputStream.getNextEntry();
        assertEquals("join-2-file", archiveEntry.getName());
    }
}
