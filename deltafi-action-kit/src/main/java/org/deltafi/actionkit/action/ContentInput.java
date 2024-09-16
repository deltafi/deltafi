/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.deltafi.actionkit.action.content.ActionContent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Action input that may include content or metadata
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class ContentInput extends MetadataInput {
    @Builder.Default
    protected List<ActionContent> content = new ArrayList<>();

    public List<ActionContent> content() {
        return content;
    }

    /**
     * @deprecated Use {@link #content()} instead.
     */
    @Deprecated
    public List<ActionContent> getContentList() {
        return content;
    }

    public void setContent(@NotNull List<ActionContent> content) {
        this.content = content;
    }

    /**
     * @deprecated Use {@link #setContent(List)} instead.
     */
    @Deprecated
    public void setContentList(@NotNull List<ActionContent> contentList) {
        this.content = contentList;
    }

    /**
     * Checks if the content list is not empty.
     * @return {@code true} if the content list is not empty, {@code false} otherwise.
     */
    public boolean hasContent() {
        return !content.isEmpty();
    }

    /**
     * Gets the content at the specified index.
     * @param index The index of the content
     * @return The first action content
     * @throws ActionKitException If no content is found in the input.
     */
    public ActionContent content(int index) {
        if (content.size() <= index) {
            throw new ActionKitException("Requested content at index " + index + ", but only " + content.size() + " are available.");
        }

        return content.get(index);
    }
}
