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
package org.deltafi.actionkit.action.converters;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.types.Content;

import java.util.List;
import java.util.Objects;

// ABOUTME: Converts between Content (common types) and ActionContent (action-kit types).
// ABOUTME: Provides static methods for bidirectional conversion of single items and lists.

/**
 * Utility class for converting between {@link Content} and {@link ActionContent}.
 */
public class ContentConverter {

    private ContentConverter() {}

    /**
     * Converts a list of {@link Content} to a list of {@link ActionContent}.
     * @param contentList the content items to convert
     * @param contentStorageService the storage service to associate with each ActionContent
     * @return a list of ActionContent instances
     */
    public static List<ActionContent> convert(List<Content> contentList, ContentStorageService contentStorageService) {
        return contentList.stream().map(content -> new ActionContent(content, contentStorageService)).toList();
    }

    /**
     * Converts a list of {@link ActionContent} to a list of {@link Content}.
     * @param actionContentList the action content items to convert, may be null
     * @return a list of Content instances, or an empty list if input is null
     */
    public static List<Content> convert(List<ActionContent> actionContentList) {
        return actionContentList != null ?
                actionContentList.stream()
                        .filter(Objects::nonNull)
                        .map(ContentConverter::convert)
                        .toList() : List.of();
    }

    /**
     * Converts a single {@link ActionContent} to a {@link Content}.
     * @param actionContent the action content to convert
     * @return the converted Content instance
     */
    public static Content convert(ActionContent actionContent) {
        return new org.deltafi.common.types.Content(actionContent.getContent().getName(),
                actionContent.getMediaType(), actionContent.getContent().getSegments(),
                actionContent.getTags());
    }
}
