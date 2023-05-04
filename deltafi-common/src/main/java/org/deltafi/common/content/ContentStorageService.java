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

import lombok.RequiredArgsConstructor;
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

    private final ObjectStorageService objectStorageService;

    public InputStream load(ContentReference contentReference) throws ObjectStorageException {
        if (contentReference.getSize() > 0) {
            if (contentReference.getSegments().size() == 1) {
                return objectStorageService.getObject(buildObjectReference(contentReference.getSegments().get(0)));
            } else {
                try {
                    return new SequenceInputStream(Collections.enumeration(contentReference.getSegments().stream()
                            .map(s -> {
                                try {
                                    return objectStorageService.getObject(buildObjectReference(s));
                                } catch (ObjectStorageException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .toList()));
                } catch (RuntimeException e) {
                    throw new ObjectStorageException(e);
                }
            }
        } else {
            return InputStream.nullInputStream();
        }

    }

    public ContentReference save(String did, byte[] content, String mediaType) throws ObjectStorageException {
        if (content.length == 0) {
            return new ContentReference(mediaType);
        }

        return save(did, new ByteArrayInputStream(content), mediaType);
    }

    public List<Content> saveMany(String did, List<SaveManyContent> saveManyContentList) throws ObjectStorageException {
        List<Content> updatedContent = new ArrayList<>();

        Map<ObjectReference, InputStream> objectsToSave = new LinkedHashMap<>();
        for (SaveManyContent entry : saveManyContentList) {
            Content content = new Content(entry.name(), null);

            if (entry.content().length == 0) {
                content.setContentReference(new ContentReference(entry.mediaType()));
                updatedContent.add(content);
                continue;
            }

            Segment segment = new Segment(did);
            segment.setSize(entry.content().length);
            content.setContentReference(new ContentReference(entry.mediaType(), segment));

            objectsToSave.put(buildObjectReference(segment), new ByteArrayInputStream(entry.content()));
            updatedContent.add(content);
        }

        objectStorageService.putObjects(CONTENT_BUCKET, objectsToSave);
        return updatedContent;
    }

    public ContentReference save(String did, InputStream inputStream, String mediaType) throws ObjectStorageException {
        Segment segment = new Segment(did);

        try(PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream)) {
            int byTe = pushbackInputStream.read();
            if (byTe == -1) {
                return new ContentReference(mediaType);
            }
            pushbackInputStream.unread(byTe);

            ObjectReference objectReference = objectStorageService.putObject(buildObjectReference(segment), pushbackInputStream);
            segment.setSize(objectReference.getSize());
            return new ContentReference(mediaType, segment);
        } catch (IOException e) {
            throw new ObjectStorageException("Error saving content " + segment.objectName(), e);
        }
    }

    public void delete(ContentReference contentReference) {
        if (contentReference.getSegments().size() == 1) {
            objectStorageService.removeObject(buildObjectReference(contentReference.getSegments().get(0)));
        } else {
            deleteAll(contentReference.getSegments());
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
}