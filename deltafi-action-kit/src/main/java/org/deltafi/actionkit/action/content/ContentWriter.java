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
package org.deltafi.actionkit.action.content;

import java.io.IOException;

// ABOUTME: Functional interface for writing a single ActionContent item.
// ABOUTME: Used as a callback by ActionContentListWriter to process each content element.

/**
 * Functional interface for writing a single {@link ActionContent} item.
 */
public interface ContentWriter {
    /**
     * Writes the given content.
     * @param actionContent the content to write
     * @throws IOException if an I/O error occurs during the write
     */
    void write(ActionContent actionContent) throws IOException;
}
