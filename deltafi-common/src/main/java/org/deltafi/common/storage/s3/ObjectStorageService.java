/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

/** Service for storing and retrieving objects in S3-compatible storage. */
public interface ObjectStorageService {

    /**
     * Retrieve an object from storage as an input stream.
     * @param objectReference the reference to the object to retrieve
     * @return an input stream of the object content
     * @throws ObjectStorageException when the object cannot be retrieved
     */
    InputStream getObject(ObjectReference objectReference) throws ObjectStorageException;

    /**
     * Retrieve an object from storage as a byte array.
     * @param objectReference the reference to the object to retrieve
     * @return the object content as a byte array
     * @throws ObjectStorageException when the object cannot be retrieved
     * @throws IOException when reading the stream fails
     */
    default byte[] getObjectAsByteArray(ObjectReference objectReference) throws ObjectStorageException, IOException {
        return getObject(objectReference).readAllBytes();
    }

    /**
     * Store an object in storage.
     * @param objectReference the reference specifying where to store the object
     * @param inputStream the content to store
     * @return the object reference with updated size information
     * @throws ObjectStorageException when the object cannot be stored
     */
    ObjectReference putObject(ObjectReference objectReference, InputStream inputStream) throws ObjectStorageException;

    /**
     * Writes the InputStream to object storage for each ObjectReference
     * @param bucket where each ObjectReference should be stored
     * @param objectsToSave map of the object references to the content input stream
     * @throws ObjectStorageException when storing the objects fails
     */
    void putObjects(String bucket, Map<ObjectReference, InputStream> objectsToSave) throws ObjectStorageException;

    /**
     * Store a byte array as an object in storage.
     * @param objectReference the reference specifying where to store the object
     * @param object the content to store
     * @return the object reference with updated size information
     * @throws ObjectStorageException when the object cannot be stored
     */
    default ObjectReference putObjectAsByteArray(ObjectReference objectReference, byte[] object) throws ObjectStorageException {
        return putObject(objectReference, new ByteArrayInputStream(object));
    }

    /**
     * Remove a single object from storage.
     * @param objectReference the reference to the object to remove
     */
    void removeObject(ObjectReference objectReference);

    /**
     * Remove multiple objects from a bucket.
     * @param bucket the bucket containing the objects
     * @param objectNames the names of the objects to remove
     * @return true if all objects were removed successfully, false if any removal failed
     */
    boolean removeObjects(String bucket, List<String> objectNames);

    /**
     * Get the size of the given object reference
     * @param objectReference to lookup
     * @return size of the object reference or -1 if it was not found
     */
    long getTotalSize(ObjectReference objectReference);

    /**
     * List all object keys in a bucket that start with the given prefix.
     * @param bucket the bucket to list
     * @param prefix key prefix to filter on (use empty string for all keys)
     * @return sorted list of matching object keys
     */
    List<String> listKeys(String bucket, String prefix);
}
