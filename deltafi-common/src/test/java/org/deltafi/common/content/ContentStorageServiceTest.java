/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class ContentStorageServiceTest {
    @Mock
    private ObjectStorageService objectStorageService;

    @InjectMocks
    private ContentStorageService contentStorageService;

    @Test
    public void loadsContent() throws ObjectStorageException, IOException {
        byte[] content = "test".getBytes();

        ContentReference contentReference = new ContentReference("uuid", 0, content.length, "did", "mediaType");

        ObjectReference objectReference = new ObjectReference("storage", "did/uuid", 0, content.length);
        Mockito.when(objectStorageService.getObject(Mockito.eq(objectReference)))
                .thenReturn(new ByteArrayInputStream(content));

        byte[] loadedContent = contentStorageService.load(contentReference).readAllBytes();
        assertArrayEquals(content, loadedContent);
    }

    @Test
    public void loadsZeroLengthContent() throws ObjectStorageException, IOException {
        InputStream inputStream = contentStorageService.load(new ContentReference("uuid", 0, 0, "did", "mediaType"));
        assertEquals(0, inputStream.readAllBytes().length);
    }

    @Test
    public void savesContent() throws ObjectStorageException {
        byte[] content = "test".getBytes();

        Mockito.when(objectStorageService.putObject(Mockito.any(), Mockito.any()))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, content.length));

        ContentReference contentReference =
                contentStorageService.save("did", new ByteArrayInputStream(content), "mediaType");

        assertEquals(0, contentReference.getOffset());
        assertEquals(content.length, contentReference.getSize());
        assertEquals("did", contentReference.getDid());
        assertEquals("mediaType", contentReference.getMediaType());
    }

    @Test
    public void savesEmptyContent() throws ObjectStorageException {
        byte[] content = {};

        ContentReference contentReference =
                contentStorageService.save("did", new ByteArrayInputStream(content), "mediaType");

        assertEquals(0, contentReference.getOffset());
        assertEquals(0, contentReference.getSize());
        assertEquals("did", contentReference.getDid());
        assertEquals("mediaType", contentReference.getMediaType());
    }

    @Test
    public void savesByteArrayContent() throws ObjectStorageException {
        byte[] content = "test".getBytes();

        Mockito.when(objectStorageService.putObject(Mockito.any(), Mockito.any()))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, content.length));

        ContentReference contentReference = contentStorageService.save("did", content, "mediaType");

        assertEquals(0, contentReference.getOffset());
        assertEquals(content.length, contentReference.getSize());
        assertEquals("did", contentReference.getDid());
        assertEquals("mediaType", contentReference.getMediaType());
    }
}
