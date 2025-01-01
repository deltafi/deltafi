/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.deltafi.common.io.Writer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class ActionContentWriter implements Writer {
    protected void writeContent(ActionContent actionContent, OutputStream outputStream) throws IOException {
        try (InputStream contentStream = actionContent.loadInputStream()) {
            contentStream.transferTo(outputStream);
        }
    }
}
