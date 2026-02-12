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
package org.deltafi.common.util;

/**
 * Thrown when a parameter template cannot be resolved or contains errors.
 */
public class ParameterTemplateException extends Exception {

    /**
     * Creates an exception with the given message.
     * @param message the detail message
     */
    public ParameterTemplateException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given cause.
     * @param cause the underlying cause
     */
    public ParameterTemplateException(Throwable cause) {
        this(null, cause);
    }

    /**
     * Creates an exception for a specific template text with the given cause.
     * @param text the template text that caused the error, or null
     * @param cause the underlying cause
     */
    public ParameterTemplateException(String text, Throwable cause) {
        super(message(text, cause.getMessage()), cause);
    }

    private static String message(String text, String message) {
        return text != null ? "Error in template '" + text + "': " + message : message;
    }
}
