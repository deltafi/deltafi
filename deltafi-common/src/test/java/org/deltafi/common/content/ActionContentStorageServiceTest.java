/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.content;

import org.assertj.core.api.Assertions;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ActionContentStorageServiceTest {

    @Captor
    ArgumentCaptor<Map<ObjectReference, InputStream>> contentMapCaptor;

    @Captor
    ArgumentCaptor<List<String>> objectNamesCaptor;

    @Mock
    private ObjectStorageService objectStorageService;

    @InjectMocks
    private ContentStorageService contentStorageService;

    private ActionContentStorageService actionStorageService;

    private static @NotNull List<SaveManyContent> getSaveManyContents() {
        byte[] firstContentBytes = "first".getBytes();
        byte[] secondContentBytes = "second".getBytes();
        byte[] emptyContentBytes = "".getBytes();

        return List.of(
                new SaveManyContent("first", MediaType.APPLICATION_OCTET_STREAM, firstContentBytes),
                new SaveManyContent("second", MediaType.APPLICATION_OCTET_STREAM, secondContentBytes),
                new SaveManyContent("empty", MediaType.APPLICATION_OCTET_STREAM, emptyContentBytes));
    }

    @BeforeEach
    public void preTest() {
        actionStorageService = new ActionContentStorageService(contentStorageService);
    }


    @Test
    public void loadsContent() throws ObjectStorageException, IOException {
        byte[] bytes = "test".getBytes();

        UUID objectId = UUID.randomUUID();
        UUID did = UUID.randomUUID();
        Segment segment = new Segment(objectId, 0, bytes.length, did);
        Content content = new Content("name", "mediaType", segment);

        ObjectReference objectReference = new ObjectReference("storage", "%s/%s/%s"
                .formatted(did.toString().substring(0, 3), did, objectId), 0, bytes.length);
        Mockito.when(objectStorageService.getObject(Mockito.eq(objectReference)))
                .thenReturn(new ByteArrayInputStream(bytes));

        byte[] loadedContent = actionStorageService.load(content).readAllBytes();
        assertArrayEquals(bytes, loadedContent);
    }

    @Test
    public void loadsZeroLengthContent() throws ObjectStorageException, IOException {
        InputStream inputStream = actionStorageService.load(new Content("name", "mediaType"));
        assertEquals(0, inputStream.readAllBytes().length);
    }

    @Test
    public void savesContent() throws ObjectStorageException {
        byte[] bytes = "test".getBytes();

        Mockito.when(objectStorageService.putObject(Mockito.any(), Mockito.any()))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length));

        UUID did = UUID.randomUUID();
        Content content = actionStorageService.save(did, new ByteArrayInputStream(bytes), "name", "mediaType");

        assertEquals(1, content.getSegments().size());
        assertEquals(0, content.getSegments().getFirst().getOffset());
        assertEquals(did, content.getSegments().getFirst().getDid());
        assertEquals(bytes.length, content.getSize());
        assertEquals("mediaType", content.getMediaType());

        assertEquals(1, actionStorageService.savedContentSize());
    }

    @Test
    public void savesEmptyContent() throws ObjectStorageException {
        byte[] bytes = {};

        Content content = actionStorageService.save(UUID.randomUUID(), new ByteArrayInputStream(bytes), "name", "mediaType");

        assertEquals(0, content.getSegments().size());
        assertEquals(0, content.getSize());
        assertEquals("mediaType", content.getMediaType());

        assertEquals(0, actionStorageService.savedContentSize());
    }

    @Test
    public void savesByteArrayContent() throws ObjectStorageException {
        byte[] bytes = "test".getBytes();

        Mockito.when(objectStorageService.putObject(Mockito.any(), Mockito.any()))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length));

        UUID did = UUID.randomUUID();
        Content content = actionStorageService.save(did, bytes, "name", "mediaType");

        assertEquals(1, content.getSegments().size());
        assertEquals(0, content.getSegments().getFirst().getOffset());
        assertEquals(did, content.getSegments().getFirst().getDid());
        assertEquals(bytes.length, content.getSize());
        assertEquals("mediaType", content.getMediaType());

        assertEquals(1, actionStorageService.savedContentSize());
    }

    @Test
    public void saveAndClearTracking() throws ObjectStorageException {
        byte[] bytes = "test".getBytes();

        Mockito.when(objectStorageService.putObject(Mockito.any(), Mockito.any()))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length));

        UUID did = UUID.randomUUID();
        Content content = actionStorageService.save(did, bytes, "name", "mediaType");

        assertEquals(1, content.getSegments().size());
        assertEquals(0, content.getSegments().getFirst().getOffset());
        assertEquals(did, content.getSegments().getFirst().getDid());
        assertEquals(bytes.length, content.getSize());
        assertEquals("mediaType", content.getMediaType());

        assertEquals(1, actionStorageService.savedContentSize());
        actionStorageService.clear();
        assertEquals(0, actionStorageService.savedContentSize());
    }

    @Test
    void saveContentMap() throws ObjectStorageException {
        List<SaveManyContent> saveManyContentList = getSaveManyContents();

        List<Content> content = actionStorageService.saveMany(UUID.randomUUID(), saveManyContentList);

        Assertions.assertThat(content).hasSize(3);
        Content first = content.get(0);
        Content second = content.get(1);
        Content empty = content.get(2);

        Assertions.assertThat(first.getSegments()).hasSize(1);
        Assertions.assertThat(second.getSegments()).hasSize(1);
        Assertions.assertThat(empty.getSegments()).isEmpty();

        Mockito.verify(objectStorageService).putObjects(Mockito.eq("storage"), contentMapCaptor.capture());
        Map<ObjectReference, InputStream> contentMap = contentMapCaptor.getValue();

        Assertions.assertThat(contentMap).hasSize(2);

        assertEquals(3, actionStorageService.savedContentSize());
    }

    @Test
    void testDeleteUnusedContent_TransformHasOrphaned() throws ObjectStorageException {
        byte[] bytes = "test".getBytes();

        Mockito.when(objectStorageService.putObject(Mockito.any(), Mockito.any()))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length));

        UUID did = UUID.randomUUID();
        UUID did2 = UUID.randomUUID();
        Content content1 = actionStorageService.save(did, bytes, "name", "mediaType");
        Content content2 = actionStorageService.save(did2, bytes, "name", "mediaType");
        Content content3 = actionStorageService.save(did, bytes, "name", "mediaType");
        assertEquals(3, actionStorageService.savedContentSize());

        ActionEvent event = ActionEvent.builder()
                .did(did)
                .type(ActionEventType.TRANSFORM)
                .transform(List.of(
                        TransformEvent.builder()
                                .content(List.of(content1, content3))
                                .build()))
                .build();

        assertEquals(1, actionStorageService.deleteUnusedContent(event));

        Mockito.verify(objectStorageService).removeObjects(Mockito.eq("storage"), objectNamesCaptor.capture());
        List<String> objectNames = objectNamesCaptor.getValue();
        assertEquals(content2.getSegments().getFirst().objectName(), objectNames.getFirst());

        assertEquals(0, actionStorageService.savedContentSize());
    }

    @Test
    void testDeleteUnusedContent_TransformNoOrphaned() throws ObjectStorageException {
        byte[] bytes = "test".getBytes();

        Mockito.when(objectStorageService.putObject(Mockito.any(), Mockito.any()))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length));

        UUID did = UUID.randomUUID();
        UUID did2 = UUID.randomUUID();
        Content content1 = actionStorageService.save(did, bytes, "name", "mediaType");
        Content content2 = actionStorageService.save(did2, bytes, "name", "mediaType");
        Content content3 = actionStorageService.save(did, bytes, "name", "mediaType");
        assertEquals(3, actionStorageService.savedContentSize());

        ActionEvent event = ActionEvent.builder()
                .did(did)
                .type(ActionEventType.TRANSFORM)
                .transform(List.of(
                        TransformEvent.builder()
                                .content(List.of(content1, content2, content3))
                                .build()))
                .build();

        assertEquals(0, actionStorageService.deleteUnusedContent(event));
        assertEquals(0, actionStorageService.savedContentSize());
    }

    @Test
    void testDeleteUnusedContent_ErrorHasOrphaned() throws ObjectStorageException {
        byte[] bytes = "test".getBytes();

        Mockito.when(objectStorageService.putObject(Mockito.any(), Mockito.any()))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length));

        UUID did = UUID.randomUUID();
        UUID did2 = UUID.randomUUID();
        Content content1 = actionStorageService.save(did, bytes, "name", "mediaType");
        Content content2 = actionStorageService.save(did2, bytes, "name", "mediaType");
        Content content3 = actionStorageService.save(did, bytes, "name", "mediaType");
        assertEquals(3, actionStorageService.savedContentSize());

        ActionEvent event = ActionEvent.builder()
                .did(did)
                .type(ActionEventType.ERROR)
                .error(ErrorEvent.builder()
                        .cause("cause")
                        .build())
                .build();

        assertEquals(3, actionStorageService.deleteUnusedContent(event));

        Mockito.verify(objectStorageService).removeObjects(Mockito.eq("storage"), objectNamesCaptor.capture());
        Set<String> objectNames = new HashSet<>(objectNamesCaptor.getValue());
        assertEquals(3, objectNames.size());

        assertTrue(objectNames.contains(content1.getSegments().getFirst().objectName()));
        assertTrue(objectNames.contains(content2.getSegments().getFirst().objectName()));
        assertTrue(objectNames.contains(content3.getSegments().getFirst().objectName()));

        assertEquals(0, actionStorageService.savedContentSize());
    }

    @Test
    void testDeleteUnusedContent_ErrorNoOrphaned() throws ObjectStorageException {
        byte[] bytes = "test".getBytes();

        UUID did = UUID.randomUUID();
        assertEquals(0, actionStorageService.savedContentSize());

        ActionEvent event = ActionEvent.builder()
                .did(did)
                .type(ActionEventType.ERROR)
                .error(ErrorEvent.builder()
                        .cause("cause")
                        .build())
                .build();

        assertEquals(0, actionStorageService.deleteUnusedContent(event));
    }

    @Test
    void testDeleteUnusedContent_IngressHasOrphaned() throws ObjectStorageException {
        byte[] bytes = "test".getBytes();

        Mockito.when(objectStorageService.putObject(Mockito.any(), Mockito.any()))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length));

        UUID did = UUID.randomUUID();
        UUID did2 = UUID.randomUUID();
        Content content1 = actionStorageService.save(did, bytes, "name", "mediaType");
        Content content2 = actionStorageService.save(did2, bytes, "name", "mediaType");
        Content content3 = actionStorageService.save(did, bytes, "name", "mediaType");
        assertEquals(3, actionStorageService.savedContentSize());

        ActionEvent event = ActionEvent.builder()
                .did(did)
                .type(ActionEventType.INGRESS)
                .ingress(IngressEvent
                        .builder()
                        .ingressItems(List.of(
                                IngressEventItem.builder().content(List.of(content1)).build(),
                                IngressEventItem.builder().content(List.of(content3)).build()))
                        .build())
                .build();

        assertEquals(1, actionStorageService.deleteUnusedContent(event));

        Mockito.verify(objectStorageService).removeObjects(Mockito.eq("storage"), objectNamesCaptor.capture());
        List<String> objectNames = objectNamesCaptor.getValue();
        assertEquals(content2.getSegments().getFirst().objectName(), objectNames.getFirst());

        assertEquals(0, actionStorageService.savedContentSize());
    }

    @Test
    void testDeleteUnusedContent_IngressNoOrphaned() throws ObjectStorageException {
        byte[] bytes = "test".getBytes();

        Mockito.when(objectStorageService.putObject(Mockito.any(), Mockito.any()))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length));

        UUID did = UUID.randomUUID();
        UUID did2 = UUID.randomUUID();
        Content content1 = actionStorageService.save(did, bytes, "name", "mediaType");
        Content content2 = actionStorageService.save(did2, bytes, "name", "mediaType");
        Content content3 = actionStorageService.save(did, bytes, "name", "mediaType");
        assertEquals(3, actionStorageService.savedContentSize());

        ActionEvent event = ActionEvent.builder()
                .did(did)
                .type(ActionEventType.INGRESS)
                .ingress(IngressEvent
                        .builder()
                        .ingressItems(List.of(
                                IngressEventItem.builder().content(List.of(content1, content2)).build(),
                                IngressEventItem.builder().content(List.of(content3)).build()))
                        .build())
                .build();

        assertEquals(0, actionStorageService.deleteUnusedContent(event));
        assertEquals(0, actionStorageService.savedContentSize());
    }


}