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
package org.deltafi.actionkit.action;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.content.ActionContent;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Slf4j
public abstract class ContentListInput {
    private List<ActionContent> contentList;

    /**
     * Return the contentList
     * @return the contentList
     */
    public List<ActionContent> content() {
        return contentList;
    }

    /**
     * Checks if the content list is not empty.
     * @return {@code true} if the content list is not empty, {@code false} otherwise.
     */
    public boolean hasContent() {
        return !contentList.isEmpty();
    }

    /**
     * Retrieves the first action content in the list.
     * @return The first action content.
     * @throws ActionKitException If no content is found in the input.
     */
    public ActionContent content(int index) {
        if (contentList.size() <= index) {
            throw new ActionKitException("Requested content at index " + index + ", but only " + contentList.size() + " are available.");
        }

        return contentList.get(index);
    }
}
