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

/** Exception thrown when an object storage operation fails. */
public class ObjectStorageException extends Exception {

    /**
     * Construct an ObjectStorageException with a message.
     * @param message description of the failure
     */
    public ObjectStorageException(String message) {
        super(message);
    }

    /**
     * Construct an ObjectStorageException with a cause.
     * @param cause the underlying cause
     */
    public ObjectStorageException(Throwable cause) {
        super(cause);
    }

    /**
     * Construct an ObjectStorageException with a message and cause.
     * @param message description of the failure
     * @param cause the underlying cause
     */
    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
