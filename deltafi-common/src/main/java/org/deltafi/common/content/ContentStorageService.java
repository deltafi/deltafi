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

import lombok.RequiredArgsConstructor;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ContentStorageService {
    private static final String CONTENT_BUCKET = "storage";
    
    private final ObjectStorageService objectStorageService;

    public InputStream load(ContentReference contentReference) throws ObjectStorageException {
        return contentReference.getSize() > 0 ? objectStorageService.getObject(buildObjectReference(contentReference)) :
                InputStream.nullInputStream();
    }

    public ContentReference save(String did, byte[] content, String mediaType) throws ObjectStorageException {
        return save(did, new ByteArrayInputStream(content), mediaType);
    }

    public ContentReference save(String did, InputStream inputStream, String mediaType) throws ObjectStorageException {
        ContentReference contentReference = new ContentReference(UUID.randomUUID().toString(), did, mediaType);
        ObjectReference objectReference = objectStorageService.putObject(
                buildObjectReference(contentReference), inputStream);
        contentReference.setSize(objectReference.getSize());
        return contentReference;
    }

    public void delete(ContentReference contentReference) {
        objectStorageService.removeObject(buildObjectReference(contentReference));
    }

    public boolean deleteAll(List<String> dids) {
        return objectStorageService.removeObjects(CONTENT_BUCKET, dids);
    }

    public Set<String> findDidsLastModifiedBefore(ZonedDateTime lastModifiedBefore) {
       return objectStorageService.getObjectNames(CONTENT_BUCKET, Collections.singletonList(""), lastModifiedBefore).stream()
                .map(objectName -> objectName.substring(0, objectName.indexOf('/')))
                .collect(Collectors.toSet());
    }

    private ObjectReference buildObjectReference(ContentReference contentReference) {
        return new ObjectReference(CONTENT_BUCKET, contentReference.getDid() + "/" + contentReference.getUuid(),
                contentReference.getOffset(), contentReference.getSize());
    }
}
