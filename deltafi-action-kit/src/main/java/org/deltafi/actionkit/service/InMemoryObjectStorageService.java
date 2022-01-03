package org.deltafi.actionkit.service;

import io.quarkus.arc.profile.UnlessBuildProfile;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
@UnlessBuildProfile("prod")
public class InMemoryObjectStorageService implements ObjectStorageService {

    // don't use this in prod, it doesn't support long-sized arrays
    private final ConcurrentMap<String, byte[]> data = new ConcurrentHashMap<>();

    public InMemoryObjectStorageService() {
        log.info(this.getClass().getSimpleName() + " instantiated");
    }

    public void clear() {
        data.clear();
    }

    @Override
    public List<String> getObjectNames(String bucket, String prefix, ZonedDateTime lastModifiedBefore) {
        return data.keySet().stream().filter(key -> key.startsWith(bucket + "/" + prefix)).collect(Collectors.toList());
    }

    @Override
    public InputStream getObject(ObjectReference objectReference) throws ObjectStorageException {
        String key = key(objectReference.getBucket(), objectReference.getName());
        if (!data.containsKey(key)) {
            throw new ObjectStorageException("Failed to get object from in-memory object storage",
                    new Exception("Object does not exist"));
        }
        byte[] object = data.get(key);
        long size = objectReference.getSize() == -1 ? (object.length - objectReference.getOffset()) :
                Math.min(objectReference.getSize(), (object.length - objectReference.getOffset()));
        return new ByteArrayInputStream(trimmedArray(object, objectReference.getOffset(), size));
    }

    private String key(String bucket, String name) {
        return bucket + "/" + name;
    }

    private byte[] trimmedArray(byte[] bytes, long offset, long size) {
        return Arrays.copyOfRange(bytes, Math.toIntExact(offset), Math.toIntExact(offset + size));
    }

    @Override
    public ObjectReference putObject(ObjectReference objectReference, InputStream inputStream) throws ObjectStorageException {
        String key = key(objectReference.getBucket(), objectReference.getName());
        try {
            data.put(key, inputStream.readAllBytes());
        } catch (IOException e) {
            throw new ObjectStorageException("Unable to read stream", e);
        }

        return new ObjectReference(objectReference.getBucket(), objectReference.getName(), 0, data.get(key).length);
    }

    @Override
    public void removeObject(ObjectReference objectReference) {
        data.remove(key(objectReference.getBucket(), objectReference.getName()));
    }

    @Override
    public boolean removeObjects(String bucket, String prefix) {
        getObjectNames(bucket, prefix).forEach(data::remove);
        return true;
    }

    @Override
    public long getObjectSize(String bucket, String name) {
        return data.get(key(bucket, name)).length;
    }
}