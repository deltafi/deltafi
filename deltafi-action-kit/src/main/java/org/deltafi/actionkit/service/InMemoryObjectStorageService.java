package org.deltafi.actionkit.service;

import io.minio.ObjectWriteResponse;
import io.quarkus.arc.profile.UnlessBuildProfile;
import lombok.extern.slf4j.Slf4j;
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
    public InputStream getObjectAsInputStream(String bucket, String name, long offset, long length) {
        return new ByteArrayInputStream(getObject(bucket, name, offset, length));
    }

    @Override
    public byte[] getObject(String bucket, String name, long offset, long length) {
        if (!data.containsKey(key(bucket, name))) {
            return null;
        }
        return trimmedArray(data.get(key(bucket, name)), offset, length);
    }

    private String key(String bucket, String name) {
        return bucket + "/" + name;
    }

    private byte[] trimmedArray(byte[] bytes, long offset, long size) {
        return Arrays.copyOfRange(bytes, Math.toIntExact(offset), Math.toIntExact(offset + size));
    }

    @Override
    public ObjectWriteResponse putObject(String bucket, String name, InputStream inputStream, long size) throws ObjectStorageException {
        try {
            data.put(key(bucket, name), inputStream.readAllBytes());
        } catch (IOException e) {
            throw new ObjectStorageException("Unable to read stream", e);
        }

        return new ObjectWriteResponse(null, bucket, null, name, null, null);
    }

    @Override
    public void removeObject(String bucket, String name) {
        data.remove(key(bucket, name));
    }

    @Override
    public boolean removeObjects(String bucket, String prefix) {
        getObjectNames(bucket, prefix).forEach(data::remove);
        return true;
    }

    @Override
    public long getObjectSize(ObjectWriteResponse writeResponse) {
        return 0;
    }
}