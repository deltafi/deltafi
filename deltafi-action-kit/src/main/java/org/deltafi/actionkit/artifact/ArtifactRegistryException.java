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
package org.deltafi.actionkit.artifact;

/**
 * Thrown when an artifact registry HTTP operation fails with a non-2xx response or
 * an unexpected transport error.
 */
class ArtifactRegistryException extends RuntimeException {

    ArtifactRegistryException(String message) {
        super(message);
    }

    ArtifactRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
