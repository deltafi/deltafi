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
// ABOUTME: Exception thrown when the action runner fails to start up.
// ABOUTME: Typically indicates a fatal initialization error like failing to write the running file.
package org.deltafi.actionkit.exception;

/**
 * Thrown when the action runner encounters a fatal error during startup
 */
public class StartupException extends RuntimeException {

    /**
     * Create a StartupException with the given reason.
     * @param reason description of the startup failure
     */
    public StartupException(String reason) {
        super(reason);
    }
}
