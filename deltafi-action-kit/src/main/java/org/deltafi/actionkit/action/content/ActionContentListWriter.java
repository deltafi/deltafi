/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public abstract class ActionContentListWriter extends ActionContentWriter {
    private final List<ActionContent> contentList;

    protected void writeContentList(ContentWriter contentWriter) throws IOException {
        List<String> errorMessages = new ArrayList<>();

        contentList.forEach(actionContent -> {
            try {
                contentWriter.write(actionContent);
            } catch (IOException e) {
                errorMessages.add(e.getMessage());
            }
        });

        if (!errorMessages.isEmpty()) {
            throw new IOException(String.join("\n", errorMessages));
        }
    }
}
