package org.deltafi.actionkit.service;

import io.quarkus.arc.profile.UnlessBuildProfile;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.exception.ContentServiceConnectException;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ObjectReference;

import javax.enterprise.context.ApplicationScoped;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.deltafi.common.constant.DeltaFiConstants.MINIO_BUCKET;

@Slf4j
@ApplicationScoped
@UnlessBuildProfile("prod")
public class InMemoryContentService implements ContentService {

    // don't use this in prod, it doesn't support long-sized arrays
    ConcurrentMap<String, byte[]> data = new ConcurrentHashMap<>();

    public InMemoryContentService() {
        log.info(this.getClass().getSimpleName() + " instantiated");
    }

    private String key(ObjectReference ref) {
        return ref.getBucket() + "/" + ref.getName();
    }

    public void get(ObjectReference ref, InputProcessor proc) throws ContentServiceConnectException, IllegalStateException {
        if (!data.containsKey(key(ref))) {
            throw new IllegalArgumentException("Could not locate content: " + ref.toString());
        } else {
            proc.operation(new ByteArrayInputStream(retrieveContent(ref)));
        }
    }

    private byte[] trimmedArray(byte[] bytes, Long offset, Long size) {
        return Arrays.copyOfRange(bytes, Math.toIntExact(offset), Math.toIntExact(size));
    }

    @SuppressWarnings("unused")
    public byte[] retrieveContent(ObjectReference ref) {
        if (!data.containsKey(key(ref))) {
            return null;
        }
        return trimmedArray(data.get(key(ref)), ref.getOffset(), ref.getSize());
    }

    @SuppressWarnings("unused")
    public ObjectReference putObject(String object, DeltaFile deltaFile, String actionName) {
        String objectName = deltaFile.getDid() + "/" + actionName;
        ObjectReference objectReference = ObjectReference.newBuilder()
                .bucket(MINIO_BUCKET)
                .name(objectName)
                .size(object.length())
                .offset(0)
                .build();
        return putObject(objectReference, object.getBytes());
    }

    public ObjectReference putObject(ObjectReference objectReference, byte[] object) {
        data.put(key(objectReference), object);
        return objectReference;
    }

    /**
     * Remove stored data for the given DeltaFile -- unsupported for in-memory
     */
    public boolean deleteObjectsForDeltaFile(DeltaFile deltaFile) {
        return false;
    }

    public void clear() {
        data.clear();
    }
}