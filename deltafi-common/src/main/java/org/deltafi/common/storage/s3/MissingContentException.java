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

import lombok.Getter;
import org.deltafi.common.content.Segment;

/** Exception thrown when referenced content does not exist in object storage. */
@Getter
public class MissingContentException extends ObjectStorageException {
    private final transient Segment segment;

    /**
     * Construct a MissingContentException with no associated segment.
     * @param message description of the missing content
     */
    public MissingContentException(String message) {
        this(null, message);
    }

    /**
     * Construct a MissingContentException for a specific content segment.
     * @param segment the content segment that was not found, or null if unknown
     * @param message description of the missing content
     */
    public MissingContentException(Segment segment, String message) {
        super(message);
        this.segment = segment;
    }
}
