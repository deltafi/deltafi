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
package org.deltafi.common.content;

import org.assertj.core.api.Assertions;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.types.Content;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class ContentStorageServiceTest {
    @Mock
    private ObjectStorageService objectStorageService;

    @InjectMocks
    private ContentStorageService contentStorageService;

    @Captor
    ArgumentCaptor<Map<ObjectReference, InputStream>> contentMapCaptor;

    @Test
    public void loadsContent() throws ObjectStorageException, IOException {
        byte[] content = "test".getBytes();

        Segment segment = new Segment("uuid", 0, content.length, "did12345");
        ContentReference contentReference = new ContentReference("mediaType", segment);

        ObjectReference objectReference = new ObjectReference("storage", "did/did12345/uuid", 0, content.length);
        Mockito.when(objectStorageService.getObject(Mockito.eq(objectReference)))
                .thenReturn(new ByteArrayInputStream(content));

        byte[] loadedContent = contentStorageService.load(contentReference).readAllBytes();
        assertArrayEquals(content, loadedContent);
    }

    @Test
    public void loadsZeroLengthContent() throws ObjectStorageException, IOException {
        InputStream inputStream = contentStorageService.load(new ContentReference("mediaType"));
        assertEquals(0, inputStream.readAllBytes().length);
    }

    @Test
    public void savesContent() throws ObjectStorageException {
        byte[] content = "test".getBytes();

        Mockito.when(objectStorageService.putObject(Mockito.any(), Mockito.any()))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, content.length));

        ContentReference contentReference =
                contentStorageService.save("did", new ByteArrayInputStream(content), "mediaType");

        assertEquals(1, contentReference.getSegments().size());
        assertEquals(0, contentReference.getSegments().get(0).getOffset());
        assertEquals("did", contentReference.getSegments().get(0).getDid());
        assertEquals(content.length, contentReference.getSize());
        assertEquals("mediaType", contentReference.getMediaType());
    }

    @Test
    public void savesEmptyContent() throws ObjectStorageException {
        byte[] content = {};

        ContentReference contentReference =
                contentStorageService.save("did", new ByteArrayInputStream(content), "mediaType");

        assertEquals(0, contentReference.getSegments().size());
        assertEquals(0, contentReference.getSize());
        assertEquals("mediaType", contentReference.getMediaType());
    }

    @Test
    public void savesByteArrayContent() throws ObjectStorageException {
        byte[] content = "test".getBytes();

        Mockito.when(objectStorageService.putObject(Mockito.any(), Mockito.any()))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, content.length));

        ContentReference contentReference = contentStorageService.save("did", content, "mediaType");

        assertEquals(1, contentReference.getSegments().size());
        assertEquals(0, contentReference.getSegments().get(0).getOffset());
        assertEquals("did", contentReference.getSegments().get(0).getDid());
        assertEquals(content.length, contentReference.getSize());
        assertEquals("mediaType", contentReference.getMediaType());
    }

    @Test
    void saveContentMap() throws ObjectStorageException {
        byte[] firstContentBytes = "first".getBytes();
        byte[] secondContentBytes = "second".getBytes();
        byte[] emptyContentBytes = "".getBytes();

        LinkedHashMap<String, byte[]> namesToBytes = new LinkedHashMap<>();
        namesToBytes.put("first", firstContentBytes);
        namesToBytes.put("second", secondContentBytes);
        namesToBytes.put("empty", emptyContentBytes);
        
        List<Content> content = contentStorageService.saveMany("abc", namesToBytes);

        Assertions.assertThat(content).hasSize(3);
        Content first = content.get(0);
        Content second = content.get(1);
        Content empty = content.get(2);

        Assertions.assertThat(first.getContentReference()).isNotNull();
        Assertions.assertThat(first.getContentReference().getSegments()).hasSize(1);

        Assertions.assertThat(second.getContentReference()).isNotNull();
        Assertions.assertThat(first.getContentReference().getSegments()).hasSize(1);
        Assertions.assertThat(empty.getContentReference()).isNotNull();
        Assertions.assertThat(empty.getContentReference().getSegments()).isEmpty();

        Mockito.verify(objectStorageService).putObjects(Mockito.eq("storage"), contentMapCaptor.capture());
        Map<ObjectReference, InputStream> contentMap = contentMapCaptor.getValue();

        Assertions.assertThat(contentMap).hasSize(2);
    }
}
