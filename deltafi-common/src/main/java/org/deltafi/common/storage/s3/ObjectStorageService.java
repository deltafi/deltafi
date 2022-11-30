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
package org.deltafi.common.storage.s3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface ObjectStorageService {

    InputStream getObject(ObjectReference objectReference) throws ObjectStorageException;

    default byte[] getObjectAsByteArray(ObjectReference objectReference) throws ObjectStorageException, IOException {
        return getObject(objectReference).readAllBytes();
    }

    ObjectReference putObject(ObjectReference objectReference, InputStream inputStream) throws ObjectStorageException;

    /**
     * Writes the InputStream to object storage for each ObjectReference
     * @param bucket where each ObjectReference should be stored
     * @param objectsToSave map of the object references to the content input stream
     * @throws ObjectStorageException when storing the objects fails
     */
    void putObjects(String bucket, Map<ObjectReference, InputStream> objectsToSave) throws ObjectStorageException;

    default ObjectReference putObjectAsByteArray(ObjectReference objectReference, byte[] object) throws ObjectStorageException {
        return putObject(objectReference, new ByteArrayInputStream(object));
    }

    void removeObject(ObjectReference objectReference);

    boolean removeObjects(String bucket, List<String> objectNames);
}
