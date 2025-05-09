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
package org.deltafi.common.test.storage.s3;

import org.apache.commons.io.input.BoundedInputStream;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryObjectStorageService implements ObjectStorageService {
    final Map<String, Map<String, byte[]>> objects = new HashMap<>();

    @Override
    public InputStream getObject(ObjectReference objectReference) {
        byte[] bytes = objects.get(objectReference.getBucket()).get(objectReference.getName());

        return new ByteArrayInputStream(subBytes(bytes, (int) objectReference.getOffset(), (int) objectReference.getSize()));
    }

    private static byte[] subBytes(byte[] bytes, int offset, int size) {
        if (bytes == null || offset < 0 || size < 0 || offset + size > bytes.length) {
            throw new IllegalArgumentException("Invalid input parameters");
        }
        byte[] result = new byte[size];
        System.arraycopy(bytes, offset, result, 0, size);
        return result;
    }

    @Override
    public ObjectReference putObject(ObjectReference objectReference, InputStream inputStream)
            throws ObjectStorageException {
        Map<String, byte[]> entry = objects.computeIfAbsent(objectReference.getBucket(), k -> new HashMap<>());
        try {
            BoundedInputStream boundedInputStream = BoundedInputStream.builder().setInputStream(inputStream).get();
            entry.put(objectReference.getName(), boundedInputStream.readAllBytes());
            return new ObjectReference(objectReference.getBucket(), objectReference.getName(), 0,
                    boundedInputStream.getCount());
        } catch (IOException e) {
            throw new ObjectStorageException(e);
        }
    }

    @Override
    public void putObjects(String bucket, Map<ObjectReference, InputStream> objectsToSave) {
        Map<String, byte[]> entry = objects.computeIfAbsent(bucket, k -> new HashMap<>());
        objectsToSave.forEach((objectReference, inputStream) -> {
            try {
                entry.put(objectReference.getName(), inputStream.readAllBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void removeObject(ObjectReference objectReference) {
        Map<String, byte[]> entry = objects.get(objectReference.getBucket());
        if (entry != null) {
            entry.remove(objectReference.getName());
        }
    }

    @Override
    public boolean removeObjects(String bucket, List<String> objectNames) {
        Map<String, byte[]> entries = objects.get(bucket);
        if (entries != null) {
            entries.entrySet().stream()
                    .filter(mapEntry -> objectNames.contains(mapEntry.getKey()))
                    .forEach(mapEntry -> entries.remove(mapEntry.getKey()));
        }
        return true;
    }
}
