package org.deltafi.common.content;

import lombok.RequiredArgsConstructor;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ContentStorageService {
    private static final String CONTENT_BUCKET = "storage";
    
    private final ObjectStorageService objectStorageService;

    public InputStream load(ContentReference contentReference) throws ObjectStorageException {
        return objectStorageService.getObject(buildObjectReference(contentReference));
    }

    public ContentReference save(String did, byte[] content) throws ObjectStorageException {
        return save(did, new ByteArrayInputStream(content));
    }

    public ContentReference save(String did, byte[] content, String mediaType) throws ObjectStorageException {
        return save(did, new ByteArrayInputStream(content), mediaType);
    }

    public ContentReference save(String did, InputStream inputStream) throws ObjectStorageException {
        return save(did, inputStream, "application/octet-stream");
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

    public boolean deleteAll(String did) {
        return objectStorageService.removeObjects(CONTENT_BUCKET, did);
    }

    public Set<String> findDidsLastModifiedBefore(ZonedDateTime lastModifiedBefore) {
       return objectStorageService.getObjectNames(CONTENT_BUCKET, "", lastModifiedBefore).stream()
                .map(objectName -> objectName.substring(0, objectName.indexOf('/')))
                .collect(Collectors.toSet());
    }

    private ObjectReference buildObjectReference(ContentReference contentReference) {
        return new ObjectReference(CONTENT_BUCKET, contentReference.getDid() + "/" + contentReference.getUuid(),
                contentReference.getOffset(), contentReference.getSize());
    }
}
