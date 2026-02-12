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
package org.deltafi.actionkit.action;

import org.jetbrains.annotations.NotNull;

// ABOUTME: General-purpose runtime exception for action-kit errors.
// ABOUTME: Thrown when an action encounters an unrecoverable internal error.

/**
 * General-purpose runtime exception for action-kit errors
 */
public class ActionKitException extends RuntimeException {
    /**
     * Create an ActionKitException with an error message.
     * @param message the error message
     */
    public ActionKitException(@NotNull String message) {
        super(message);
    }

    /**
     * Create an ActionKitException with an error message and cause.
     * @param message the error message
     * @param e the underlying cause
     */
    public ActionKitException(@NotNull String message, @NotNull Throwable e) {
        super(message, e);
    }
}
