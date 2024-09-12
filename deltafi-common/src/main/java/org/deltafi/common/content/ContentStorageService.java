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
package org.deltafi.common.content;

import lombok.RequiredArgsConstructor;
import org.deltafi.common.storage.s3.MissingContentException;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.SaveManyContent;

import java.io.*;
import java.util.*;

@RequiredArgsConstructor
public class ContentStorageService {
    public static final String CONTENT_BUCKET = "storage";

    final ObjectStorageService objectStorageService;

    public InputStream load(Content content) throws ObjectStorageException {
        if (content.getSize() == 0) {
            return InputStream.nullInputStream();
        }

        return content.getSegments().size() == 1 ?
                getObject(content.getSegments().getFirst()) :
                getObjects(content.getSegments());
    }

    private InputStream getObjects(List<Segment> segments) throws ObjectStorageException {
        try {
            return new SequenceInputStream(Collections.enumeration(segments.stream()
                    .map(this::getObjectWrapExceptions).toList()));
        } catch (ObjectStorageRuntimeException e) {
            throw e.objectStorageException;
        } catch (RuntimeException e) {
            throw new ObjectStorageException(e);
        }
    }

    private InputStream getObjectWrapExceptions(Segment segment) {
        try {
            return getObject(segment);
        } catch (ObjectStorageException e) {
            throw new ObjectStorageRuntimeException(e);
        }
    }

    /*
      Get the InputStream for the given segment. If the content is missing add the segment
      object to the exception and rethrow it
     */
    private InputStream getObject(Segment segment) throws ObjectStorageException {
        try {
            return objectStorageService.getObject(buildObjectReference(segment));
        } catch (MissingContentException e) {
            throw new MissingContentException(segment, e.getMessage());
        }
    }

    public Content save(UUID did, byte[] content, String name, String mediaType) throws ObjectStorageException {
        if (content.length == 0) {
            return new Content(name, mediaType, Collections.emptyList());
        }

        return save(did, new ByteArrayInputStream(content), name, mediaType);
    }

    public List<Content> saveMany(UUID did, List<SaveManyContent> saveManyContentList) throws ObjectStorageException {
        List<Content> updatedContent = new ArrayList<>();

        Map<ObjectReference, InputStream> objectsToSave = new LinkedHashMap<>();
        for (SaveManyContent entry : saveManyContentList) {
            List<Segment> segments = new ArrayList<>();

            if (entry.content().length > 0) {
                Segment segment = new Segment(did);
                segment.setSize(entry.content().length);
                segments.add(segment);
                objectsToSave.put(buildObjectReference(segment), new ByteArrayInputStream(entry.content()));
            }

            Content content = new Content(entry.name(), entry.mediaType(), segments);

            updatedContent.add(content);
        }

        objectStorageService.putObjects(CONTENT_BUCKET, objectsToSave);
        return updatedContent;
    }

    public Content save(UUID did, InputStream inputStream, String name, String mediaType) throws ObjectStorageException {
        Segment segment = new Segment(did);

        PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream);
        try {
            int byTe = pushbackInputStream.read();
            if (byTe == -1) {
                return new Content(name, mediaType);
            }
            pushbackInputStream.unread(byTe);

            ObjectReference objectReference = objectStorageService.putObject(buildObjectReference(segment), pushbackInputStream);
            segment.setSize(objectReference.getSize());
            return new Content(name, mediaType, List.of(segment));
        } catch (IOException e) {
            throw new ObjectStorageException("Error saving content " + segment.objectName(), e);
        }
    }

    public void delete(Content content) {
        if (content.getSegments().size() == 1) {
            objectStorageService.removeObject(buildObjectReference(content.getSegments().getFirst()));
        } else {
            deleteAll(content.getSegments());
        }
    }

    public void deleteAll(List<Segment> segments) {
        if (!segments.isEmpty()) {
            objectStorageService.removeObjects(CONTENT_BUCKET, segments.stream()
                    .map(Segment::objectName)
                    .distinct()
                    .toList());
        }
    }

    private ObjectReference buildObjectReference(Segment segment) {
        return new ObjectReference(CONTENT_BUCKET, segment.objectName(),
                segment.getOffset(), segment.getSize());
    }

    private static class ObjectStorageRuntimeException extends RuntimeException {
        private final ObjectStorageException objectStorageException;

        ObjectStorageRuntimeException(ObjectStorageException objectStorageException) {
            this.objectStorageException = objectStorageException;
        }
    }
}