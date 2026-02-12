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
// ABOUTME: Exception thrown when an action requires metadata at a key that does not exist.
// ABOUTME: Caught by the action runner and converted to an ErrorResult.
package org.deltafi.actionkit.exception;

import lombok.Getter;

/**
 * Thrown when an action attempts to access metadata at a key that does not exist
 */
@Getter
public class MissingMetadataException extends IllegalArgumentException {
    /** The metadata key that was not found */
    final String key;

    /**
     * Create a MissingMetadataException for the given key.
     * @param key the metadata key that was not found
     */
    public MissingMetadataException(String key) {
        this.key = key;
    }

}
